# 海盐盐场 · 结晶池与收盐作业管理

以「结晶池 + 卤度 + 收盐作业」为主线的盐场生产管理系统。

## 端口

| 服务 | 端口 |
| --- | --- |
| 前端 Vue 3 | 8285 |
| 后端 Spring Boot | 8385 |
| MySQL 8 | 3585 |
| Redis 7 | 6585 |

## 启动

```bash
./start.sh          # 秒起；首次或改动后 ./start.sh --build
./stop.sh           # 暂停
./resume.sh         # 恢复
./clean.sh          # 收摊（down -v，连 mysql 匿名卷一起删）
```

## 技术栈

- 后端：Spring Boot 3.3.0 / Java 17 / Spring Data JPA（Hibernate 6.5）/ MySQL 8 / Redis
- 前端：Vue 3 / Element Plus / Vite（主题色 `#1b5e20`）
- 建表：`ddl-auto: none`，表与种子数据全部来自 `mysql/schema.sql`

## 数据访问的两条标识

1. **Hibernate 6 `@Any` 多态关联**
   - `field_log`（作业记录）与 `dispatch_task`（场务调度任务）两张表都用
     `target_type` + `target_id` 指向 `pond` / `salt_platform` / `equipment` 三种不同实体中的任意一种，
     由 `@Any + @AnyDiscriminator(STRING) + @AnyDiscriminatorValues + @AnyKeyJavaClass(Long.class)` 映射。
   - `GET /api/field-logs` 直接返回解析后的 `targetType`、`targetTypeName`、`targetId`、`targetName`，
     同一批数据里会同时出现「池子 / 坨台 / 设备」三类目标。
   - `GET /api/dispatch` 是第二个落点。
2. **手写 JPQL `@Query`**
   - `PondAreaRepository#statBrineByArea`：按池区统计卤度均值、池数、面积
   - `WeatherLogRepository#statDaily`：按日期汇总蒸发量 / 降雨量 / 平均卤度
   - `WeatherLogRepository#findRecentRain`：窗口期降雨记录（雨后禁收判据）
   - `SaltPlatformRepository#statStockAndOutbound`：坨台库存与发运汇总
   - `HarvestTaskRepository#statByTeam`：按班组汇总收盐吨数与工时

## 业务规则（service 层抛 `BizException`）

| 规则 | 触发点 | 提示 |
| --- | --- | --- |
| 卤度未达 25°Bé 不允许收盐 | `HarvestService#create` / `RainStopService#resumeBlockers` | 给出当前值与门槛 |
| 雨后 24 小时内不允许收盐 | `HarvestService#create` / `#updateStatus` | 给出降雨时刻与雨量、最早复工时刻 |
| 池板状况「需修」不允许排作业 | `HarvestService#create` / `#resumeBlockers` | 给出池号 |
| 有效降雨立即停工 | `WeatherService#create → RainStopService#applyRainfall` | 同池 PLANNED/WORKING 单自动转 RAIN_STOP；DONE/CANCELED 不动 |
| 复工三条件 | `POST /api/harvest/{id}/resume` | 满 24h + 卤度≥25°Bé + 池板非需修，不满足时逐条列出拦截项；停工期间再降雨按新降雨重新起算 |
| 登记预占不得超过可用库存 | `OutboundService#register` | 给出账面库存、待复核预占、可用库存与本次吨数 |
| 只有待复核单可确认/作废 | `OutboundService#confirm / #reject` | 给出单号与当前状态 |
| 只有已确认单可冲正重记 | `OutboundService#reverseAndReRecord` | 待复核单引导走「作废后重记」 |
| 车牌直改仅限车牌变化 | `OutboundService#correctPlate` | 坨台/吨数/客户由后端锁定不接收，新车牌与原牌相同则拒绝 |
| 冲正重记新单可用库存必须够 | `OutboundService#reverseAndReRecord` | 不足时整事务回滚，原单仍为已确认，绝不留半截账 |
| 禁止重复冲正 | `OutboundService#reverseAndReRecord` | 状态判断 + 行锁 + `uk_outbound_reversal_of` 唯一约束三重兜底 |

### 出仓「登记预占 — 复核确认 — 冲正重记」闭环

- **库存口径（权衡后选择「优先预占后实扣」）**：司磅员登记后单据为 `PENDING`，吨数立即
  **预占冻结**（`可用库存 = 账面库存 − 待复核预占`，新登记以此为上限，防止超卖），但
  **账面库存不动**、累计发运不计；复核员确认（`CONFIRMED`）后预占才**转实扣**，账面减吨。
  未选「实扣后释放」：那种方式登记即减账面，复核期间财务账已动，错单要先补账再重记，
  异常恢复复杂且易重复恢复；预占方式下待复核错单直接**作废释放**即可，账面从未变化、
  无账实差异。页面顶部常驻口径说明，卡片/汇总表都展示「账面库存 / 预占中 / 可用 / 累计实发」。
- **状态机**：`登记 PENDING ──复核确认──▶ CONFIRMED（实扣）`；`PENDING ──作废──▶ REJECTED（释放预占）`；
  `CONFIRMED ──冲正──▶ REVERSED（库存原子恢复）──同事务重记──▶ 新 CONFIRMED 单`。
- **两条纠错路径（都保留原值 / 新值 / 操作人 / 时间到 `outbound_correction`）**：
  - 仅车牌错误（坨台、吨数、客户均不变）→ **直接更正**（`PLATE`），不动库存，可多次更正；
  - 吨数 / 坨台 / 客户错误 → **冲正重记**（`REVERSAL`）：原单先冲正恢复库存，同事务内再按
    新单扣减，新单直接 CONFIRMED；跨坨时按平台 id 升序加锁防死锁。
- **并发与原子**：登记 / 确认 / 作废 / 直改 / 冲正全部在事务内先对坨台行 `PESSIMISTIC_WRITE`
  再加出仓单行锁，同坨库存与单据状态在数据库层串行化，杜绝并发确认；冲正「恢复→扣减」一个
  事务原子完成，配合 `uk_outbound_reversal_of` 唯一约束，禁止重复冲正、库存不会恢复两次。
- **可追溯、不可删除**：系统不提供删除接口，`REVERSED / REJECTED` 单永久留流水；
  冲正链路用 `reversal_of_id`（新单指向原单）/ `reversed_by_id`（原单指向新单）双向挂接，
  刷新后状态完全由持久化字段装配。


### 雨后停工 — 复工确认闭环

- **状态机**：`PLANNED / WORKING ──有效降雨──▶ RAIN_STOP（记录 prevStatus、停工原因、起算时刻、
  最早复工时刻、触发降雨与雨量快照）──调度员确认复工──▶ 恢复 prevStatus`。`DONE/CANCELED`
  永远不参与停工；`RAIN_STOP` 不能被人工直接改入/改出。
- **同步显示**：班次条停工单（蓝灰雨丝样式）、池子平面图蓝雨标、池区树/chips 雨停角标，
  都带停工原因、起算时刻、最早可复工时刻；池子视图额外返回同池停工单数。
- **并发**：降雨登记、作业下达/开始/完成、复工确认全部在事务内先对池行加
  `PESSIMISTIC_WRITE` 行锁，同池变化在数据库层串行化；窗口内开始作业或完成入账一律拦截，
  不会出现「既运行又停工 / 重复停工记录 / 绕过雨后窗口入账」。同一条降雨重复应用幂等。
- **可追溯**：每次自动停工写「雨情自动停工」作业记录，停工期间再降雨写「雨情窗口顺延」，
  人工复工写「雨后复工确认」，均经 `field_log`（@Any 指向池子）留痕；刷新页面后状态、时间、
  原因从持久化字段与最新雨情一致装配；启动时 `RainStopReconciler` 按窗口内最新降雨自动对账。

## 模块

1. 盐田与池区：池区类型（蒸发 / 调节 / 结晶）、面积、纳潮口、责任人
2. 池子台账：池号、面积亩、池板状况、当前卤度、卤水深度、作业状态
3. 卤度与气象记录：每日每池卤度、蒸发量、降雨量、气温、风力
4. 收盐作业与班次：作业单、班组、时长、吨数、机械/人工、坨台去向、状态
5. 坨台与盐坨库存：盐种、库存吨、垛形、含水率、出仓与客户发运

## 前端四个页面

| 路径 | 页面 | 操作范式 |
| --- | --- | --- |
| `/ponds` | 盐田平面 SVG + 卤度热力 | 平面多边形按卤度着色 + 色带图例 + 右侧抽屉登记 |
| `/harvest` | 收盐作业班次条 | 横条按时长比例绘制 + 右侧累计进度环 + 点条改状态 |
| `/weather` | 气象蒸发量折线 | 主/次双折线 + 雨日蓝色竖标 + 点数据点看明细 |
| `/platforms` | 盐坨库存立方体卡 | CSS 3D 立方体按库存缩放 + 登记预占 + 出仓复核/冲正重记 |

导航形态：左侧常驻池区树（池区 → 池号）+ 顶部可横向滚动的池号 chips 条。
