-- =============================================================
-- 海盐盐场 · 结晶池与收盐作业管理  ——  建表 + 种子数据
-- 库名：salt_field    （Hibernate ddl-auto: none，表全部由本文件建好）
-- =============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS pond_area (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(32) NOT NULL UNIQUE COMMENT '池区编码',
  name        VARCHAR(64) NOT NULL COMMENT '池区名称',
  area_type   VARCHAR(16) NOT NULL COMMENT '池区类型 EVAP蒸发池区/REG调节池区/CRYST结晶池区',
  area_mu     DECIMAL(10,2) COMMENT '池区面积（亩）',
  tide_gate   VARCHAR(64) COMMENT '纳潮口',
  owner_name  VARCHAR(32) COMMENT '责任人',
  band_index  INT COMMENT '平面图自上而下的条带序号',
  remark      VARCHAR(128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盐田池区';

CREATE TABLE IF NOT EXISTS pond (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(64) NOT NULL COMMENT '池名',
  pond_no      VARCHAR(32) NOT NULL UNIQUE COMMENT '池号',
  area_id      BIGINT NOT NULL COMMENT '所属池区',
  area_mu      DECIMAL(10,2) COMMENT '面积（亩）',
  board_status VARCHAR(16) COMMENT '池板状况 GOOD完好/LEAK渗漏/REPAIR需修',
  brine_degree DECIMAL(5,2) COMMENT '当前卤度 °Bé',
  brine_depth  DECIMAL(5,2) COMMENT '卤水深度 cm',
  pond_status  VARCHAR(16) COMMENT '状态 SUN晒卤/CRYST结晶/WAIT待收/REPAIR修池',
  grid_row     INT COMMENT '平面图行号',
  grid_col     INT COMMENT '平面图列号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='池子台账';

CREATE TABLE IF NOT EXISTS weather_log (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  pond_id      BIGINT NOT NULL COMMENT '池子',
  record_date  DATE NOT NULL COMMENT '记录日期',
  recorded_at  DATETIME COMMENT '测量时刻（雨后24小时规则判据）',
  brine_degree DECIMAL(5,2) COMMENT '卤度 °Bé',
  evaporation  DECIMAL(6,2) COMMENT '蒸发量 mm',
  rainfall     DECIMAL(6,2) COMMENT '降雨量 mm',
  temperature  DECIMAL(5,2) COMMENT '气温 ℃',
  wind_level   INT COMMENT '风力 级',
  recorder     VARCHAR(32) COMMENT '记录人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卤度与气象记录';

CREATE TABLE IF NOT EXISTS harvest_task (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_no         VARCHAR(32) UNIQUE COMMENT '作业单号',
  pond_id         BIGINT NOT NULL COMMENT '池号',
  platform_id     BIGINT COMMENT '坨台去向',
  work_date       DATE COMMENT '作业日期',
  team_name       VARCHAR(32) COMMENT '班组',
  start_hour      DECIMAL(4,1) COMMENT '开工时刻（小时）',
  duration_hours  DECIMAL(4,1) COMMENT '作业时长（小时）',
  tons            DECIMAL(8,2) COMMENT '收盐吨数',
  work_mode       VARCHAR(16) COMMENT '作业方式 MACHINE机械/MANUAL人工',
  task_status     VARCHAR(16) COMMENT '状态 PLANNED待作业/WORKING作业中/RAIN_STOP雨情停工/DONE已完成/CANCELED已取消',
  prev_status     VARCHAR(16) COMMENT '雨情停工前的状态 PLANNED/WORKING，复工后恢复',
  stop_reason     VARCHAR(128) COMMENT '停工原因（雨情：降雨量 + 雨后窗口）',
  stop_started_at DATETIME COMMENT '停工起算时刻（有效降雨登记时刻）',
  resume_at       DATETIME COMMENT '最早可复工时刻（起算 + 24 小时）',
  rain_log_id     BIGINT COMMENT '触发停工/重新起算窗口的气象记录',
  rainfall_snapshot DECIMAL(6,2) COMMENT '停工时的降雨量 mm',
  operator        VARCHAR(32) COMMENT '调度员'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收盐作业与班次';

CREATE TABLE IF NOT EXISTS salt_platform (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(64) NOT NULL COMMENT '坨台名称',
  platform_no   VARCHAR(32) NOT NULL UNIQUE COMMENT '坨台编号',
  salt_type     VARCHAR(16) COMMENT '盐种 INDUSTRIAL工业盐/SUN日晒盐/REFINED精制盐',
  stock_tons    DECIMAL(10,2) COMMENT '库存（吨）',
  capacity_tons DECIMAL(10,2) COMMENT '容量（吨）',
  pile_shape    VARCHAR(32) COMMENT '垛形',
  moisture      DECIMAL(5,2) COMMENT '含水率 %',
  location      VARCHAR(64) COMMENT '所在区域'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坨台与盐坨库存';

CREATE TABLE IF NOT EXISTS outbound_record (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  platform_id      BIGINT NOT NULL COMMENT '坨台',
  customer         VARCHAR(64) COMMENT '客户',
  tons             DECIMAL(8,2) COMMENT '出仓吨数',
  plate_no         VARCHAR(32) COMMENT '车牌',
  out_date         DATE COMMENT '出仓日期',
  operator         VARCHAR(32) COMMENT '司磅员（登记人）',
  status           VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '单据状态 PENDING待复核/CONFIRMED已确认/REJECTED已作废/REVERSED已冲正',
  confirm_operator VARCHAR(32) COMMENT '复核确认人',
  confirm_at       DATETIME COMMENT '复核确认时刻',
  reject_operator  VARCHAR(32) COMMENT '作废操作人',
  reject_at        DATETIME COMMENT '作废时刻',
  reject_reason    VARCHAR(128) COMMENT '作废原因',
  reversal_of_id   BIGINT COMMENT '本单由哪张已确认单冲正重记而来（重记新单指向原单）',
  reversed_by_id   BIGINT COMMENT '本单被哪张重记单冲正（原单指向重记新单）',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登记时刻',
  version          BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 乐观锁版本：并发确认/冲正由数据库层兜底，防重复实扣',
  KEY idx_outbound_status (status),
  KEY idx_outbound_platform_status (platform_id, status),
  UNIQUE KEY uk_outbound_reversal_of (reversal_of_id) COMMENT '一张原单最多被冲正一次：并发冲正由数据库兜底拒绝'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坨台出仓记录（待复核/已确认/已作废/已冲正）';

-- 出仓更正流水：车牌直改、冲正重记两条路径都保留原值/新值/操作人/时间
CREATE TABLE IF NOT EXISTS outbound_correction (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  change_type        VARCHAR(16) NOT NULL COMMENT '更正类型 PLATE车牌直改/REVERSAL冲正重记',
  record_id          BIGINT NOT NULL COMMENT '被更正的出仓单（车牌直改=本单；冲正=原单）',
  new_record_id      BIGINT COMMENT '重记产生的新出仓单（仅冲正重记）',
  old_platform_id    BIGINT COMMENT '原值-坨台',
  new_platform_id    BIGINT COMMENT '新值-坨台',
  old_customer       VARCHAR(64) COMMENT '原值-客户',
  new_customer       VARCHAR(64) COMMENT '新值-客户',
  old_tons           DECIMAL(8,2) COMMENT '原值-吨数',
  new_tons           DECIMAL(8,2) COMMENT '新值-吨数',
  old_plate_no       VARCHAR(32) COMMENT '原值-车牌',
  new_plate_no       VARCHAR(32) COMMENT '新值-车牌',
  operator           VARCHAR(32) COMMENT '操作人',
  operated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时刻',
  remark             VARCHAR(128) COMMENT '备注/原因',
  KEY idx_correction_record (record_id),
  KEY idx_correction_new (new_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出仓更正流水（车牌直改 / 冲正重记，原值新值全留痕）';

CREATE TABLE IF NOT EXISTS equipment (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(64) NOT NULL COMMENT '设备名称',
  equip_no      VARCHAR(32) NOT NULL UNIQUE COMMENT '设备编号',
  equip_type    VARCHAR(32) COMMENT '设备类型',
  equip_status  VARCHAR(16) COMMENT '状态 RUNNING在用/IDLE闲置/FAULT故障',
  location      VARCHAR(64) COMMENT '所在位置',
  last_maintain DATE COMMENT '上次保养日期'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盐场设备';

CREATE TABLE IF NOT EXISTS field_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  log_type    VARCHAR(16) COMMENT '记录类型 BRINE测卤/HARVEST收盐/OUTBOUND出仓/REPAIR修池/INSPECT巡检',
  target_type VARCHAR(16) COMMENT '多态关联对象类型 POND池子/PLATFORM坨台/EQUIPMENT设备',
  target_id   BIGINT COMMENT '多态关联对象主键',
  content     VARCHAR(255) COMMENT '记录内容',
  operator    VARCHAR(32) COMMENT '操作人',
  log_time    DATETIME COMMENT '记录时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业记录（Hibernate @Any 多态关联：池子/坨台/设备）';

CREATE TABLE IF NOT EXISTS dispatch_task (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_no     VARCHAR(32) UNIQUE COMMENT '任务单号',
  task_type   VARCHAR(16) COMMENT '任务类型 DREDGE清淤/FIX修池/TRANSFER转卤/UPKEEP设备保养',
  target_type VARCHAR(16) COMMENT '多态关联对象类型 POND池子/PLATFORM坨台/EQUIPMENT设备',
  target_id   BIGINT COMMENT '多态关联对象主键',
  crew        VARCHAR(32) COMMENT '班组',
  plan_date   DATE COMMENT '计划日期',
  task_status VARCHAR(16) COMMENT '状态 TODO待派/DOING进行中/DONE已完成（历史行可能为空值/旧别名/非法值，由应用层归一化判定）',
  last_operator VARCHAR(32) COMMENT '最后一次有效推进的操作人',
  status_changed_at DATETIME COMMENT '当前状态生效时刻（每次推进与状态同事务写入）',
  remark      VARCHAR(128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场务调度任务（Hibernate @Any 多态关联）';

-- 派工状态流转留痕：每次有效推进保留前态/后态/原始值/操作人/时间；
-- 业务更新与留痕同事务共同成功或回滚；幂等提交不产生新行（网络重试不会重复留痕）
CREATE TABLE IF NOT EXISTS dispatch_status_log (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id         BIGINT NOT NULL COMMENT '派工任务',
  change_type     VARCHAR(16) NOT NULL COMMENT '变更类型 CREATE下达/ADVANCE推进/NORMALIZE历史别名归一化/REPAIR异常修正',
  from_status     VARCHAR(16) COMMENT '归一化前态 TODO/DOING/DONE',
  to_status       VARCHAR(16) COMMENT '归一化后态 TODO/DOING/DONE',
  raw_from_status VARCHAR(64) COMMENT '库中原始前态（别名/空值/非法值原样保留，审计真相）',
  operator        VARCHAR(32) COMMENT '操作人（系统归一化为 系统）',
  operated_at     DATETIME NOT NULL COMMENT '操作时刻',
  remark          VARCHAR(128) COMMENT '备注/修正理由',
  KEY idx_dispatch_log_task (task_id, operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工任务状态流转留痕（原值新值全留痕，只追加不修改）';

-- ---------------- 种子数据 ----------------

-- 池区（5）
INSERT INTO pond_area (code, name, area_type, area_mu, tide_gate, owner_name, band_index, remark) VALUES
  ('A01','东滩蒸发池区','EVAP',1280.00,'东闸一号纳潮口','张海生',1,'纳潮主力区，卤水逐级提浓'),
  ('A02','中滩调节池区','REG',860.00,'中闸二号纳潮口','李长贵',2,'承接蒸发池来卤，调节饱和度'),
  ('A03','西滩结晶池区','CRYST',640.00,'西闸三号纳潮口','王立新',3,'主结晶区，达标后下达收盐作业'),
  ('A04','北滩结晶池区','CRYST',520.00,'北闸四号纳潮口','赵德水',4,'备用结晶区，含一座待修池'),
  ('A05','南滩蒸发池区','EVAP',960.00,'南闸五号纳潮口','孙保田',5,'新开滩面，卤度偏低');

-- 池子台账（14）
INSERT INTO pond (name, pond_no, area_id, area_mu, board_status, brine_degree, brine_depth, pond_status, grid_row, grid_col) VALUES
  ('一号蒸发池','E-01',1,320.00,'GOOD',10.50,45.00,'SUN',0,0),
  ('二号蒸发池','E-02',1,320.00,'GOOD',12.00,48.00,'SUN',0,1),
  ('三号蒸发池','E-03',1,320.00,'LEAK',9.80,42.00,'SUN',0,2),
  ('一号调节池','R-01',2,280.00,'GOOD',18.20,36.00,'SUN',0,0),
  ('二号调节池','R-02',2,280.00,'GOOD',19.60,38.00,'SUN',0,1),
  ('三号调节池','R-03',2,280.00,'GOOD',17.40,35.00,'SUN',0,2),
  ('一号结晶池','C-01',3,160.00,'GOOD',26.40,22.00,'WAIT',0,0),
  ('二号结晶池','C-02',3,160.00,'GOOD',25.80,20.00,'WAIT',0,1),
  ('三号结晶池','C-03',3,160.00,'GOOD',24.10,24.00,'CRYST',1,0),
  ('四号结晶池','C-04',3,160.00,'GOOD',21.50,26.00,'CRYST',1,1),
  ('五号结晶池','C-05',4,260.00,'GOOD',27.30,19.00,'WAIT',0,0),
  ('六号结晶池','C-06',4,260.00,'REPAIR',13.00,30.00,'REPAIR',0,1),
  ('四号蒸发池','E-04',5,480.00,'GOOD',11.20,44.00,'SUN',0,0),
  ('五号蒸发池','E-05',5,480.00,'GOOD',8.60,40.00,'SUN',0,1);

-- 卤度与气象记录：6 个代表池 × 近 14 天
INSERT INTO weather_log (pond_id, record_date, recorded_at, brine_degree, evaporation, rainfall, temperature, wind_level, recorder) VALUES
  (1, (DATE_SUB(CURDATE(), INTERVAL 13 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 13 DAY)), ' 08:00:00'), 6.34, 3.20, 0.00, 22.50, 2, '周测卤'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 12 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 12 DAY)), ' 08:00:00'), 6.81, 5.75, 0.00, 26.00, 3, '吴气象'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 11 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 11 DAY)), ' 08:00:00'), 7.28, 8.30, 0.00, 28.50, 4, '郑守滩'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 10 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 10 DAY)), ' 08:00:00'), 7.30, 5.10, 0.00, 23.00, 5, '周测卤'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 9 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 9 DAY)), ' 08:00:00'), 7.77, 6.65, 3.40, 25.50, 6, '吴气象'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 8 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 8 DAY)), ' 08:00:00'), 8.24, 3.45, 0.00, 29.00, 2, '郑守滩'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 7 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 7 DAY)), ' 08:00:00'), 8.26, 6.00, 0.00, 22.50, 3, '周测卤'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 6 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 6 DAY)), ' 08:00:00'), 8.73, 8.55, 0.00, 26.00, 4, '吴气象'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 5 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 5 DAY)), ' 08:00:00'), 9.20, 4.35, 8.20, 28.50, 5, '郑守滩'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 4 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 4 DAY)), ' 08:00:00'), 9.22, 6.90, 0.00, 23.00, 6, '周测卤'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 3 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 3 DAY)), ' 08:00:00'), 9.69, 3.70, 0.00, 25.50, 2, '吴气象'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 2 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 2 DAY)), ' 08:00:00'), 10.16, 6.25, 0.00, 29.00, 3, '郑守滩'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 1 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 1 DAY)), ' 08:00:00'), 10.18, 7.80, 0.00, 22.50, 4, '周测卤'),
  (1, (DATE_SUB(CURDATE(), INTERVAL 0 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 0 DAY)), ' 08:00:00'), 10.65, 4.60, 0.00, 26.00, 5, '吴气象'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 13 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 13 DAY)), ' 08:00:00'), 14.04, 3.20, 0.00, 22.50, 2, '周测卤'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 12 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 12 DAY)), ' 08:00:00'), 14.51, 5.75, 0.00, 26.00, 3, '吴气象'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 11 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 11 DAY)), ' 08:00:00'), 14.98, 8.30, 0.00, 28.50, 4, '郑守滩'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 10 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 10 DAY)), ' 08:00:00'), 15.00, 5.10, 0.00, 23.00, 5, '周测卤'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 9 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 9 DAY)), ' 08:00:00'), 15.47, 6.65, 3.40, 25.50, 6, '吴气象'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 8 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 8 DAY)), ' 08:00:00'), 15.94, 3.45, 0.00, 29.00, 2, '郑守滩'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 7 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 7 DAY)), ' 08:00:00'), 15.96, 6.00, 0.00, 22.50, 3, '周测卤'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 6 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 6 DAY)), ' 08:00:00'), 16.43, 8.55, 0.00, 26.00, 4, '吴气象'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 5 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 5 DAY)), ' 08:00:00'), 16.90, 4.35, 8.20, 28.50, 5, '郑守滩'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 4 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 4 DAY)), ' 08:00:00'), 16.92, 6.90, 0.00, 23.00, 6, '周测卤'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 3 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 3 DAY)), ' 08:00:00'), 17.39, 3.70, 0.00, 25.50, 2, '吴气象'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 2 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 2 DAY)), ' 08:00:00'), 17.86, 6.25, 0.00, 29.00, 3, '郑守滩'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 1 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 1 DAY)), ' 08:00:00'), 17.88, 7.80, 0.00, 22.50, 4, '周测卤'),
  (4, (DATE_SUB(CURDATE(), INTERVAL 0 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 0 DAY)), ' 08:00:00'), 18.35, 4.60, 0.00, 26.00, 5, '吴气象'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 13 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 13 DAY)), ' 08:00:00'), 22.24, 3.20, 0.00, 22.50, 2, '周测卤'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 12 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 12 DAY)), ' 08:00:00'), 22.71, 5.75, 0.00, 26.00, 3, '吴气象'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 11 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 11 DAY)), ' 08:00:00'), 23.18, 8.30, 0.00, 28.50, 4, '郑守滩'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 10 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 10 DAY)), ' 08:00:00'), 23.20, 5.10, 0.00, 23.00, 5, '周测卤'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 9 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 9 DAY)), ' 08:00:00'), 23.67, 6.65, 3.40, 25.50, 6, '吴气象'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 8 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 8 DAY)), ' 08:00:00'), 24.14, 3.45, 0.00, 29.00, 2, '郑守滩'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 7 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 7 DAY)), ' 08:00:00'), 24.16, 6.00, 0.00, 22.50, 3, '周测卤'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 6 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 6 DAY)), ' 08:00:00'), 24.63, 8.55, 0.00, 26.00, 4, '吴气象'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 5 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 5 DAY)), ' 08:00:00'), 25.10, 4.35, 8.20, 28.50, 5, '郑守滩'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 4 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 4 DAY)), ' 08:00:00'), 25.12, 6.90, 0.00, 23.00, 6, '周测卤'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 3 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 3 DAY)), ' 08:00:00'), 25.59, 3.70, 0.00, 25.50, 2, '吴气象'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 2 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 2 DAY)), ' 08:00:00'), 26.06, 6.25, 0.00, 29.00, 3, '郑守滩'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 1 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 1 DAY)), ' 20:00:00'), 26.08, 7.80, 12.50, 22.50, 4, '周测卤'),
  (7, (DATE_SUB(CURDATE(), INTERVAL 0 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 0 DAY)), ' 08:00:00'), 26.55, 4.60, 0.00, 26.00, 5, '吴气象'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 13 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 13 DAY)), ' 08:00:00'), 21.64, 3.20, 0.00, 22.50, 2, '周测卤'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 12 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 12 DAY)), ' 08:00:00'), 22.11, 5.75, 0.00, 26.00, 3, '吴气象'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 11 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 11 DAY)), ' 08:00:00'), 22.58, 8.30, 0.00, 28.50, 4, '郑守滩'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 10 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 10 DAY)), ' 08:00:00'), 22.60, 5.10, 0.00, 23.00, 5, '周测卤'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 9 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 9 DAY)), ' 08:00:00'), 23.07, 6.65, 3.40, 25.50, 6, '吴气象'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 8 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 8 DAY)), ' 08:00:00'), 23.54, 3.45, 0.00, 29.00, 2, '郑守滩'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 7 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 7 DAY)), ' 08:00:00'), 23.56, 6.00, 0.00, 22.50, 3, '周测卤'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 6 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 6 DAY)), ' 08:00:00'), 24.03, 8.55, 0.00, 26.00, 4, '吴气象'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 5 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 5 DAY)), ' 08:00:00'), 24.50, 4.35, 8.20, 28.50, 5, '郑守滩'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 4 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 4 DAY)), ' 08:00:00'), 24.52, 6.90, 0.00, 23.00, 6, '周测卤'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 3 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 3 DAY)), ' 08:00:00'), 24.99, 3.70, 0.00, 25.50, 2, '吴气象'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 2 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 2 DAY)), ' 08:00:00'), 25.46, 6.25, 0.00, 29.00, 3, '郑守滩'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 1 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 1 DAY)), ' 08:00:00'), 25.48, 7.80, 0.00, 22.50, 4, '周测卤'),
  (8, (DATE_SUB(CURDATE(), INTERVAL 0 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 0 DAY)), ' 06:00:00'), 25.95, 4.60, 6.00, 26.00, 5, '吴气象'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 13 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 13 DAY)), ' 08:00:00'), 19.94, 3.20, 0.00, 22.50, 2, '周测卤'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 12 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 12 DAY)), ' 08:00:00'), 20.41, 5.75, 0.00, 26.00, 3, '吴气象'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 11 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 11 DAY)), ' 08:00:00'), 20.88, 8.30, 0.00, 28.50, 4, '郑守滩'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 10 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 10 DAY)), ' 08:00:00'), 20.90, 5.10, 0.00, 23.00, 5, '周测卤'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 9 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 9 DAY)), ' 08:00:00'), 21.37, 6.65, 3.40, 25.50, 6, '吴气象'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 8 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 8 DAY)), ' 08:00:00'), 21.84, 3.45, 0.00, 29.00, 2, '郑守滩'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 7 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 7 DAY)), ' 08:00:00'), 21.86, 6.00, 0.00, 22.50, 3, '周测卤'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 6 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 6 DAY)), ' 08:00:00'), 22.33, 8.55, 0.00, 26.00, 4, '吴气象'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 5 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 5 DAY)), ' 08:00:00'), 22.80, 4.35, 8.20, 28.50, 5, '郑守滩'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 4 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 4 DAY)), ' 08:00:00'), 22.82, 6.90, 0.00, 23.00, 6, '周测卤'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 3 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 3 DAY)), ' 08:00:00'), 23.29, 3.70, 0.00, 25.50, 2, '吴气象'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 2 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 2 DAY)), ' 08:00:00'), 23.76, 6.25, 0.00, 29.00, 3, '郑守滩'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 1 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 1 DAY)), ' 08:00:00'), 23.78, 7.80, 0.00, 22.50, 4, '周测卤'),
  (9, (DATE_SUB(CURDATE(), INTERVAL 0 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 0 DAY)), ' 08:00:00'), 24.25, 4.60, 0.00, 26.00, 5, '吴气象'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 13 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 13 DAY)), ' 08:00:00'), 23.14, 3.20, 0.00, 22.50, 2, '周测卤'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 12 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 12 DAY)), ' 08:00:00'), 23.61, 5.75, 0.00, 26.00, 3, '吴气象'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 11 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 11 DAY)), ' 08:00:00'), 24.08, 8.30, 0.00, 28.50, 4, '郑守滩'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 10 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 10 DAY)), ' 08:00:00'), 24.10, 5.10, 0.00, 23.00, 5, '周测卤'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 9 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 9 DAY)), ' 08:00:00'), 24.57, 6.65, 3.40, 25.50, 6, '吴气象'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 8 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 8 DAY)), ' 08:00:00'), 25.04, 3.45, 0.00, 29.00, 2, '郑守滩'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 7 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 7 DAY)), ' 08:00:00'), 25.06, 6.00, 0.00, 22.50, 3, '周测卤'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 6 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 6 DAY)), ' 08:00:00'), 25.53, 8.55, 0.00, 26.00, 4, '吴气象'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 5 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 5 DAY)), ' 08:00:00'), 26.00, 4.35, 8.20, 28.50, 5, '郑守滩'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 4 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 4 DAY)), ' 08:00:00'), 26.02, 6.90, 0.00, 23.00, 6, '周测卤'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 3 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 3 DAY)), ' 08:00:00'), 26.49, 3.70, 0.00, 25.50, 2, '吴气象'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 2 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 2 DAY)), ' 08:00:00'), 26.96, 6.25, 0.00, 29.00, 3, '郑守滩'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 1 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 1 DAY)), ' 08:00:00'), 26.98, 7.80, 0.00, 22.50, 4, '周测卤'),
  (11, (DATE_SUB(CURDATE(), INTERVAL 0 DAY)), CONCAT((DATE_SUB(CURDATE(), INTERVAL 0 DAY)), ' 08:00:00'), 27.45, 4.60, 0.00, 26.00, 5, '吴气象');

-- 收盐作业单（12，含班组与坨台去向）
INSERT INTO harvest_task (task_no, pond_id, platform_id, work_date, team_name, start_hour, duration_hours, tons, work_mode, task_status, operator) VALUES
  ('HS20260927001',7,1,(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'甲班',5.5,6.5,86.00,'MACHINE','DONE','王立新'),
  ('HS20260927002',11,2,(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'乙班',6.0,5.0,64.50,'MACHINE','WORKING','赵德水'),
  ('HS20260927003',9,1,(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'丙班',7.0,4.5,52.00,'MANUAL','PLANNED','王立新'),
  ('HS20260927004',8,4,(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'甲班',13.0,4.0,48.00,'MACHINE','WORKING','王立新'),
  ('HS20260927005',10,5,(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'丁班',14.5,3.5,36.50,'MANUAL','PLANNED','赵德水'),
  ('HS20260927006',11,3,(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'乙班',15.0,2.5,28.00,'MACHINE','PLANNED','赵德水'),
  ('HS20260926001',7,1,(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'甲班',5.0,7.0,92.50,'MACHINE','DONE','王立新'),
  ('HS20260926002',8,2,(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'丙班',6.5,5.5,71.00,'MACHINE','DONE','王立新'),
  ('HS20260926003',9,4,(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'乙班',8.0,4.0,45.50,'MANUAL','DONE','赵德水'),
  ('HS20260926004',10,5,(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'丁班',13.5,3.0,31.00,'MANUAL','CANCELED','赵德水'),
  ('HS20260925001',11,1,(DATE_SUB(CURDATE(), INTERVAL 2 DAY)),'甲班',5.5,6.0,78.00,'MACHINE','DONE','王立新'),
  ('HS20260925002',7,3,(DATE_SUB(CURDATE(), INTERVAL 2 DAY)),'丙班',7.5,4.5,49.50,'MACHINE','DONE','王立新');

-- 坨台（6）
INSERT INTO salt_platform (name, platform_no, salt_type, stock_tons, capacity_tons, pile_shape, moisture, location) VALUES
  ('一号坨台','T-01','INDUSTRIAL',1850.00,3000.00,'锥形垛',3.20,'西坨区'),
  ('二号坨台','T-02','SUN',1240.00,2000.00,'长条垛',4.50,'东坨区'),
  ('三号坨台','T-03','REFINED',680.00,1200.00,'方垛',1.80,'精盐库'),
  ('四号坨台','T-04','INDUSTRIAL',2260.00,3000.00,'锥形垛',3.60,'西坨区'),
  ('五号坨台','T-05','SUN',940.00,1800.00,'长条垛',5.10,'东坨区'),
  ('六号坨台','T-06','REFINED',320.00,1200.00,'方垛',1.50,'精盐库');

-- 出仓记录（10；旧账全部视为「已确认」，补齐登记/确认时刻；两张待复核演示单随后插入）
INSERT INTO outbound_record (platform_id, customer, tons, plate_no, out_date, operator, status, created_at, confirm_at) VALUES
  (1,'渤海化工集团',320.00,'冀B·A1276',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'刘司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 09:05:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 09:12:00')),
  (1,'滨海氯碱厂',180.50,'冀B·C3391',(DATE_SUB(CURDATE(), INTERVAL 2 DAY)),'刘司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 10:20:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 10:26:00')),
  (2,'通达盐业公司',240.00,'鲁G·D8802',(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'陈司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 14:02:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 14:08:00')),
  (3,'恒晟食品配料',95.00,'冀B·F5520',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'刘司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 08:30:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 08:36:00')),
  (3,'海晶调味品厂',60.00,'冀B·F5521',(DATE_SUB(CURDATE(), INTERVAL 4 DAY)),'陈司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 4 DAY), ' 09:40:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 4 DAY), ' 09:46:00')),
  (4,'北方纯碱股份',410.00,'津A·K0093',(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'刘司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 11:00:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 11:07:00')),
  (4,'长芦盐化运销',265.00,'津A·K0095',(DATE_SUB(CURDATE(), INTERVAL 3 DAY)),'陈司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), ' 15:12:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), ' 15:19:00')),
  (5,'东海冷链物流',150.00,'鲁G·D7716',(DATE_SUB(CURDATE(), INTERVAL 2 DAY)),'陈司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 13:25:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 13:31:00')),
  (5,'盛通贸易商行',88.00,'鲁G·D7718',(DATE_SUB(CURDATE(), INTERVAL 5 DAY)),'刘司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 5 DAY), ' 10:48:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 5 DAY), ' 10:55:00')),
  (6,'精制盐直营店',42.00,'冀B·L2210',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'刘司磅','CONFIRMED',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 07:55:00'),CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 08:01:00'));

-- 待复核演示单（2）：登记即预占、尚未实扣，可用库存已被冻结
INSERT INTO outbound_record (platform_id, customer, tons, plate_no, out_date, operator, status, created_at) VALUES
  (2,'通达盐业公司',55.00,'鲁G·D8809',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'陈司磅','PENDING',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 16:02:00')),
  (4,'北方纯碱股份',120.00,'津A·K0101',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'刘司磅','PENDING',
    CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 16:18:00'));

-- 设备（8）
INSERT INTO equipment (name, equip_no, equip_type, equip_status, location, last_maintain) VALUES
  ('一号收盐机','EQ-01','收盐机','RUNNING','西滩结晶池区',(DATE_SUB(CURDATE(), INTERVAL 6 DAY))),
  ('二号收盐机','EQ-02','收盐机','RUNNING','北滩结晶池区',(DATE_SUB(CURDATE(), INTERVAL 9 DAY))),
  ('一号提卤泵','EQ-03','提卤泵','RUNNING','东滩蒸发池区',(DATE_SUB(CURDATE(), INTERVAL 12 DAY))),
  ('二号提卤泵','EQ-04','提卤泵','IDLE','南滩蒸发池区',(DATE_SUB(CURDATE(), INTERVAL 21 DAY))),
  ('三号提卤泵','EQ-05','提卤泵','FAULT','中滩调节池区',(DATE_SUB(CURDATE(), INTERVAL 30 DAY))),
  ('推土机一台','EQ-06','推土机','IDLE','坨台区',(DATE_SUB(CURDATE(), INTERVAL 15 DAY))),
  ('一号输送带','EQ-07','输送带','RUNNING','坨台区',(DATE_SUB(CURDATE(), INTERVAL 8 DAY))),
  ('二号输送带','EQ-08','输送带','IDLE','坨台区',(DATE_SUB(CURDATE(), INTERVAL 18 DAY)));

-- 作业记录（12）：@Any 多态关联，target_type 覆盖 POND / PLATFORM / EQUIPMENT 三类
INSERT INTO field_log (log_type, target_type, target_id, content, operator, log_time) VALUES
  ('BRINE','POND',7,'测卤 26.40°Bé，卤水深 22cm，池板完好','周测卤',CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 07:40:00')),
  ('HARVEST','POND',7,'下达收盐作业 HS20260927001，甲班计划收盐 86 吨','王立新',CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 05:30:00')),
  ('OUTBOUND','PLATFORM',1,'出仓 320 吨 → 渤海化工集团（冀B·A1276）','刘司磅',CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 09:10:00')),
  ('INSPECT','EQUIPMENT',1,'一号收盐机例行巡检，液压与输送皮带正常','孙机修',CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 06:20:00')),
  ('REPAIR','POND',12,'六号结晶池池板破损，已挂牌待修，暂停排产','赵德水',CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 10:05:00')),
  ('BRINE','POND',9,'测卤 24.10°Bé，未达 25°Bé 门槛，继续结晶','周测卤',CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 07:50:00')),
  ('HARVEST','PLATFORM',2,'作业单 HS20260926002 完成，二号坨台入库 71 吨','王立新',CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 16:30:00')),
  ('INSPECT','EQUIPMENT',5,'三号提卤泵电机异响，判定故障待件','孙机修',CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 14:40:00')),
  ('BRINE','POND',1,'测卤 10.50°Bé，卤水深 45cm，正常走卤','吴气象',CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 08:00:00')),
  ('OUTBOUND','PLATFORM',3,'出仓 95 吨 → 恒晟食品配料（冀B·F5520）','刘司磅',CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), ' 11:25:00')),
  ('INSPECT','POND',5,'二号调节池巡滩，池板完好、卤水无外溢','李长贵',CONCAT(DATE_SUB(CURDATE(), INTERVAL 4 DAY), ' 09:15:00')),
  ('INSPECT','EQUIPMENT',7,'一号输送带张紧度调整，运行正常','孙机修',CONCAT(DATE_SUB(CURDATE(), INTERVAL 5 DAY), ' 15:50:00'));

-- 场务调度任务（8）：第二个 @Any 落点，同样覆盖三类目标对象
INSERT INTO dispatch_task (task_no, task_type, target_type, target_id, crew, plan_date, task_status, last_operator, status_changed_at, remark) VALUES
  ('DP-1001','FIX','POND',12,'维修一班',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'DOING','赵德水',CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 08:10:00'),'六号结晶池池板重铺'),
  ('DP-1002','DREDGE','POND',3,'清淤二班',(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'TODO','王立新',CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 09:00:00'),'三号蒸发池池底清淤'),
  ('DP-1003','TRANSFER','POND',4,'走水一班',(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'DONE','李长贵',CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 15:40:00'),'一号调节池向结晶区转卤'),
  ('DP-1004','UPKEEP','EQUIPMENT',5,'机修班',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'TODO','王立新',CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 07:30:00'),'三号提卤泵更换轴承'),
  ('DP-1005','UPKEEP','EQUIPMENT',4,'机修班',(DATE_SUB(CURDATE(), INTERVAL 2 DAY)),'DONE','孙机修',CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 11:20:00'),'二号提卤泵换油保养'),
  ('DP-1006','DREDGE','PLATFORM',1,'坨台班',(DATE_SUB(CURDATE(), INTERVAL 2 DAY)),'TODO','李长贵',CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 10:00:00'),'一号坨台清底并整理垛形'),
  ('DP-1007','FIX','POND',10,'维修二班',(DATE_SUB(CURDATE(), INTERVAL 3 DAY)),'DONE','赵德水',CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), ' 16:05:00'),'四号结晶池池埂补漏'),
  ('DP-1008','TRANSFER','POND',8,'走水二班',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'DOING','李长贵',CONCAT(DATE_SUB(CURDATE(), INTERVAL 0 DAY), ' 08:45:00'),'二号结晶池达标转卤待收');

-- 历史脏数据（4）：兼容口径——旧别名启动时归一化并留痕、任务可用；
-- 空值与非法值原样保留、标记异常并冻结流转，只能经「修正异常」留痕入口改入合法状态
INSERT INTO dispatch_task (task_no, task_type, target_type, target_id, crew, plan_date, task_status, remark) VALUES
  ('DP-1009','FIX','POND',6,'维修一班',(DATE_SUB(CURDATE(), INTERVAL 2 DAY)),NULL,'二号调节池旧台账迁移，状态字段为空'),
  ('DP-1010','DREDGE','POND',2,'清淤一班',(DATE_SUB(CURDATE(), INTERVAL 1 DAY)),'DO','二号蒸发池清淤（旧系统英文缩写 DO）'),
  ('DP-1011','TRANSFER','POND',5,'走水一班',(DATE_SUB(CURDATE(), INTERVAL 3 DAY)),'已完成','三号调节池转卤（旧系统中文值）'),
  ('DP-1012','UPKEEP','EQUIPMENT',3,'机修班',(DATE_SUB(CURDATE(), INTERVAL 0 DAY)),'XYZ','一号提卤泵保养（脏状态，来源不明）');

