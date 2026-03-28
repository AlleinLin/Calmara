-- Calmara 心理健康智能系统数据库初始化脚本
-- 创建时间: 2024
-- 数据库: PostgreSQL

-- 用户表
CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    role VARCHAR(20) DEFAULT 'USER',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_user_username ON "user"(username);
CREATE INDEX IF NOT EXISTS idx_user_email ON "user"(email);
CREATE INDEX IF NOT EXISTS idx_user_role ON "user"(role);
COMMENT ON TABLE "user" IS '用户表';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    emotion_label VARCHAR(50),
    emotion_score DOUBLE PRECISION,
    risk_level VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_user FOREIGN KEY (user_id) REFERENCES "user"(id)
);
CREATE INDEX IF NOT EXISTS idx_chat_message_session_id ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_user_id ON chat_message(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_created_at ON chat_message(created_at);
COMMENT ON TABLE chat_message IS '聊天消息表';
COMMENT ON COLUMN chat_message.role IS 'USER or ASSISTANT';

-- 情绪记录表
CREATE TABLE IF NOT EXISTS emotion_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    text_emotion VARCHAR(50),
    text_score DOUBLE PRECISION,
    audio_emotion VARCHAR(50),
    audio_score DOUBLE PRECISION,
    visual_emotion VARCHAR(50),
    visual_score DOUBLE PRECISION,
    fusion_emotion VARCHAR(50),
    fusion_score DOUBLE PRECISION,
    risk_level VARCHAR(20),
    session_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_emotion_record_user FOREIGN KEY (user_id) REFERENCES "user"(id)
);
CREATE INDEX IF NOT EXISTS idx_emotion_record_user_id ON emotion_record(user_id);
CREATE INDEX IF NOT EXISTS idx_emotion_record_risk_level ON emotion_record(risk_level);
CREATE INDEX IF NOT EXISTS idx_emotion_record_created_at ON emotion_record(created_at);
COMMENT ON TABLE emotion_record IS '情绪记录表';

-- 预警记录表
CREATE TABLE IF NOT EXISTS alert_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    handler VARCHAR(100),
    handle_time TIMESTAMP NULL,
    handle_note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_record_user FOREIGN KEY (user_id) REFERENCES "user"(id)
);
CREATE INDEX IF NOT EXISTS idx_alert_record_user_id ON alert_record(user_id);
CREATE INDEX IF NOT EXISTS idx_alert_record_status ON alert_record(status);
CREATE INDEX IF NOT EXISTS idx_alert_record_risk_level ON alert_record(risk_level);
COMMENT ON TABLE alert_record IS '预警记录表';
COMMENT ON COLUMN alert_record.status IS 'PENDING, PROCESSING, RESOLVED';

-- 管理员邮箱表
CREATE TABLE IF NOT EXISTS admin_email (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100) NOT NULL UNIQUE,
    department VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_admin_email_email ON admin_email(email);
CREATE INDEX IF NOT EXISTS idx_admin_email_is_active ON admin_email(is_active);
COMMENT ON TABLE admin_email IS '管理员邮箱表';

-- 知识文档表
CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50),
    keywords TEXT,
    source VARCHAR(100),
    view_count INT DEFAULT 0,
    usefulness INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_category ON knowledge_document(category);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_created_at ON knowledge_document(created_at);
COMMENT ON TABLE knowledge_document IS '知识文档表';

-- 创建全文搜索索引 (PostgreSQL)
CREATE INDEX IF NOT EXISTS idx_knowledge_document_content_search ON knowledge_document 
    USING gin(to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, '')));

-- 数据获取监控表
CREATE TABLE IF NOT EXISTS data_acquisition_log (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(100) NOT NULL,
    file_path VARCHAR(500),
    size BIGINT,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_data_acquisition_log_source ON data_acquisition_log(source);
CREATE INDEX IF NOT EXISTS idx_data_acquisition_log_status ON data_acquisition_log(status);
CREATE INDEX IF NOT EXISTS idx_data_acquisition_log_created_at ON data_acquisition_log(created_at);
COMMENT ON TABLE data_acquisition_log IS '数据获取监控表';
COMMENT ON COLUMN data_acquisition_log.status IS 'SUCCESS or FAILED';

-- 插入默认管理员
INSERT INTO "user" (username, password, email, role, status) 
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@calmara.edu', 'ADMIN', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- 插入默认管理员邮箱
INSERT INTO admin_email (name, email, department, is_active) 
VALUES 
    ('系统管理员', 'admin@calmara.edu', '心理中心', TRUE),
    ('心理咨询师', 'counselor@calmara.edu', '心理咨询部', TRUE)
ON CONFLICT (email) DO NOTHING;

-- 插入示例知识文档
INSERT INTO knowledge_document (title, content, category, keywords, source) VALUES
('大学生常见心理问题及应对策略', '大学生常见的心理问题包括：学业压力、人际关系困扰、情感问题、就业焦虑、自我认同困惑等。应对策略：1. 学业压力：制定合理学习计划，寻求老师同学帮助，参加学习小组；2. 人际关系：学会倾听和表达，参加社团活动，培养社交技能；3. 情感问题：理性看待恋爱，学会处理分手，必要时寻求心理咨询；4. 就业焦虑：提前规划职业方向，参加实习实践，提升综合能力；5. 自我认同：探索兴趣爱好，参与志愿活动，建立积极的自我认知。学校心理咨询中心提供免费咨询服务，建议及时寻求专业帮助。', '大学生心理', '大学生,学业压力,人际关系,情感问题,就业焦虑', '系统内置'),
('考试焦虑的识别与缓解', '考试焦虑表现为：心跳加速、出汗、手抖、注意力难以集中、记忆力下降、睡眠障碍等。缓解方法：1. 认知调整：将考试视为检验学习的机会，而非威胁；2. 充分准备：制定复习计划，提前准备，增强信心；3. 放松训练：考试前进行深呼吸、渐进式肌肉放松；4. 积极暗示：对自己说''我已经准备好了''、''我能做到''；5. 时间管理：考试时合理分配时间，先易后难；6. 身体照顾：考前充足睡眠、合理饮食、适度运动。如果焦虑严重影响考试表现，建议寻求学校心理老师帮助。', '考试心理', '考试焦虑,考试紧张,考前焦虑,考试压力,考试心理', '系统内置'),
('宿舍人际关系处理指南', '宿舍人际关系问题常见于：生活习惯差异、作息时间冲突、卫生习惯不同、个人空间界限模糊等。处理建议：1. 建立宿舍公约：开学初共同制定宿舍规则，明确作息、卫生、访客等事项；2. 有效沟通：有问题及时沟通，使用''我''语言表达感受，避免指责；3. 相互尊重：尊重他人习惯和隐私，不随意使用他人物品；4. 寻求妥协：遇到分歧时寻求双方都能接受的解决方案；5. 保持独立：培养自己的兴趣爱好，不要过度依赖室友；6. 寻求帮助：如果矛盾无法解决，可寻求辅导员或心理咨询师帮助。记住：宿舍是集体生活空间，相互理解和包容是和谐相处的关键。', '人际关系', '宿舍关系,室友矛盾,宿舍冲突,人际关系,宿舍生活', '系统内置'),
('失恋后的心理调适', '失恋是大学生常见的情感困扰，可能带来悲伤、愤怒、自责、焦虑等情绪。调适方法：1. 允许悲伤：给自己时间哀悼这段关系，哭泣和难过是正常的；2. 寻求支持：与朋友家人倾诉，不要独自承受；3. 避免自责：失恋不是你的错，不要过度反思和自责；4. 切断联系：暂时避免与前任联系，给自己空间恢复；5. 转移注意力：投入学习、工作、兴趣爱好，丰富生活；6. 自我成长：反思这段关系中的收获，为未来做准备；7. 寻求专业帮助：如果持续抑郁超过两周，或出现自伤念头，立即寻求心理咨询。记住：失恋是成长的一部分，时间会治愈一切。', '情感心理', '失恋,分手,情感困扰,恋爱问题,情感调适', '系统内置'),
('社交焦虑的识别与克服', '社交焦虑表现为：在社交场合感到紧张、害怕被评价、回避社交活动、担心出丑等。克服方法：1. 认知重构：挑战负面想法，如''大家都在看我''、''我会出丑''；2. 暴露疗法：逐步面对恐惧的社交场合，从小范围开始；3. 社交技能训练：练习眼神接触、微笑、倾听、提问等基本技能；4. 放松技巧：社交前做深呼吸、肌肉放松，缓解紧张；5. 关注他人：将注意力从自己转移到对话内容和对方身上；6. 接受不完美：允许自己犯错，没有人是完美的；7. 寻求支持：加入支持小组或寻求专业心理咨询。记住：社交焦虑是可以克服的，关键是逐步练习和持续努力。', '社交心理', '社交焦虑,社交恐惧,害羞,怕人,社交困难', '系统内置'),
('学业倦怠的应对策略', '学业倦怠表现为：对学习失去兴趣、感到疲惫、效率下降、拖延、逃避学习等。应对策略：1. 识别原因：是课程难度、学习压力、目标不明确还是其他原因；2. 调整目标：设定具体、可实现的小目标，逐步建立信心；3. 改变方法：尝试新的学习方法，如番茄工作法、思维导图等；4. 寻求帮助：向老师、同学或学习中心寻求学业支持；5. 休息调整：保证充足睡眠，适度运动，培养兴趣爱好；6. 重新连接：思考学习的意义，找到内在动力；7. 专业帮助：如果倦怠持续影响学业，寻求心理咨询。记住：学业倦怠是常见现象，及时调整可以重新找回学习动力。', '学习心理', '学业倦怠,学习疲劳,学习动力不足,拖延,学习困难', '系统内置'),
('就业焦虑的心理调适', '就业焦虑常见于：担心找不到工作、害怕面试失败、对未来迷茫、比较心理等。调适方法：1. 提前规划：尽早明确职业方向，制定求职计划；2. 提升能力：参加实习、培训、考证，增强竞争力；3. 信息收集：了解就业市场、行业趋势、企业需求；4. 模拟练习：进行简历修改、模拟面试，提升求职技能；5. 调整心态：接受就业竞争的现实，保持积极心态；6. 多元选择：不要局限于单一选择，拓宽就业视野；7. 寻求支持：利用学校就业指导中心、校友资源等；8. 心理咨询：如果焦虑严重影响生活，寻求专业帮助。记住：就业是人生的重要阶段，保持信心和行动力是关键。', '就业心理', '就业焦虑,求职压力,就业迷茫,面试紧张,职业规划', '系统内置'),
('家庭矛盾的心理应对', '家庭矛盾可能包括：父母期望压力、亲子沟通障碍、家庭经济压力、父母关系问题等。应对方法：1. 理解父母：尝试理解父母的立场和担忧，他们通常出于关心；2. 有效沟通：选择合适时机，用''我''语言表达感受，避免指责；3. 设定边界：在尊重的前提下，明确自己的底线和需求；4. 寻求妥协：寻找双方都能接受的解决方案；5. 保持独立：培养自己的支持系统，不要过度依赖家庭；6. 专业帮助：如果家庭矛盾严重影响心理健康，寻求家庭治疗或心理咨询。记住：家庭关系需要双方努力，但你的心理健康同样重要。', '家庭心理', '家庭矛盾,亲子关系,家庭压力,父母期望,家庭问题', '系统内置'),
('自我伤害行为的识别与干预', '自我伤害行为包括：割伤、烫伤、撞击等故意伤害自己的行为，通常是应对强烈情绪的方式。警示信号：身上有不明伤痕、穿长袖遮盖、情绪剧烈波动、社交退缩等。干预措施：1. 立即关注：认真对待任何自伤行为，不要忽视或轻视；2. 表达关心：以非评判的态度表达关心和支持；3. 寻求专业帮助：立即联系学校心理中心、辅导员或医院精神科；4. 移除危险物品：移除刀具、药物等可能用于自伤的物品；5. 陪伴支持：不要让当事人独处，提供持续陪伴；6. 危机干预：如有自杀风险，立即拨打心理危机热线400-161-9995或送医。记住：自伤行为是心理痛苦的信号，需要专业帮助和支持。', '危机干预', '自伤,自残,自我伤害,危机,自杀风险', '系统内置'),
('网络成瘾的识别与戒除', '网络成瘾表现为：无法控制上网时间、影响学业和生活、戒断症状（焦虑、烦躁）、对其他活动失去兴趣等。戒除方法：1. 承认问题：认识到网络成瘾对生活的影响；2. 设定限制：制定上网时间表，使用软件限制上网时间；3. 替代活动：培养线下兴趣爱好，如运动、阅读、社交；4. 环境改变：减少上网的触发因素，如将电脑移出卧室；5. 寻求支持：告诉朋友家人你的目标，寻求监督和支持；6. 专业帮助：如果无法自我控制，寻求心理咨询或成瘾治疗。记住：网络是工具而非生活的全部，平衡是关键。', '成瘾行为', '网络成瘾,网瘾,游戏成瘾,手机依赖,网络依赖', '系统内置'),
('完美主义的心理调适', '完美主义表现为：设定过高标准、害怕失败、过度自我批评、拖延、难以满足等。调适方法：1. 认识完美主义：理解完美主义是焦虑的表现，而非追求卓越；2. 设定现实目标：制定可实现的目标，接受''足够好''；3. 重构失败：将失败视为学习机会，而非个人缺陷；4. 自我同情：对自己宽容，像对待朋友一样对待自己；5. 关注过程：享受努力的过程，而非只关注结果；6. 寻求反馈：接受他人的建设性意见，不要过度自我批评；7. 专业帮助：如果完美主义严重影响生活，寻求认知行为治疗。记住：完美是不存在的，接受不完美是心理健康的表现。', '人格特质', '完美主义,追求完美,自我要求高,害怕失败,过度自我批评', '系统内置'),
('时间管理与效率提升', '有效的时间管理可以减少压力、提高效率、增加成就感。核心方法：1. 四象限法则：按重要性和紧急性将任务分类，优先处理重要紧急的事；2. 待办清单：每天列出要完成的任务，按优先级排序；3. 番茄工作法：25分钟专注工作+5分钟休息，提高专注力；4. 时间块：为不同任务分配固定时间段，避免多任务切换；5. 消除干扰：关闭手机通知，创造专注环境；6. 学会拒绝：不接受超出能力的任务，保护自己的时间；7. 定期回顾：每周评估时间使用情况，持续优化。记住：时间管理的目的是更好地生活，而非填满每一分钟。', '自我管理', '时间管理,效率,拖延,计划,时间', '系统内置'),
('情绪调节的有效方法', '情绪调节是心理健康的重要技能。有效方法：1. 情绪识别：准确识别自己的情绪，给情绪命名，如''我现在感到焦虑''；2. 情绪接纳：允许自己有负面情绪，不批判、不压抑；3. 情绪表达：通过谈话、写作、绘画、运动等方式表达情绪；4. 认知重评：改变对事件的解释，寻找积极意义；5. 注意力转移：做喜欢的事情，暂时离开负面情绪；6. 放松技巧：深呼吸、冥想、瑜伽、渐进式肌肉放松；7. 寻求支持：与信任的人交流或寻求专业帮助。记住：情绪是暂时的，学会调节情绪是终身受益的技能。', '情绪管理', '情绪调节,情绪管理,情绪控制,情绪问题,情绪', '系统内置'),
('正念冥想入门指南', '正念冥想是一种心理训练方法，帮助人们活在当下、减少焦虑抑郁。基本练习：1. 呼吸冥想：专注呼吸，感受空气进出鼻腔，思绪飘走时温和地拉回；2. 身体扫描：从头到脚感受身体各部位，注意紧张和放松；3. 行走冥想：专注行走时的身体感觉，感受脚与地面的接触；4. 日常正念：吃饭、洗澡、走路时专注当下，不做其他事。练习建议：每天10-20分钟，选择安静环境，保持舒适姿势，初学者可使用冥想APP辅助。益处：减少焦虑抑郁、提高注意力、改善睡眠、增强情绪调节能力。记住：正念是练习，不是完美，思绪飘走是正常的。', '心理技术', '正念,冥想,放松,专注,正念冥想', '系统内置'),
('心理咨询的常见误区', '关于心理咨询的常见误区：误区1：''有精神病才需要心理咨询''。事实：心理咨询适用于各种生活困扰、情绪问题、个人成长，不限于严重心理疾病。误区2：''心理咨询就是聊天''。事实：心理咨询是专业的心理干预，咨询师受过系统训练，使用科学方法帮助来访者。误区3：''咨询师会告诉我该怎么做''。事实：咨询师帮助你探索问题、发现资源、做出自己的决定，而非直接给建议。误区4：''心理咨询一次就能解决''。事实：心理咨询通常需要多次，是一个过程而非快速解决方案。误区5：''心理咨询会泄露隐私''。事实：咨询师有严格的保密义务，除非涉及自伤、伤人等法律规定的例外情况。记住：寻求心理咨询是勇敢和明智的选择，是对自己负责的表现。', '心理咨询', '心理咨询,心理治疗,咨询师,心理帮助,心理咨询误区', '系统内置'),
('危机干预资源', '心理危机时请立即寻求帮助！全国心理援助热线：400-161-9995（24小时）；北京心理危机研究与干预中心：010-82951332；生命热线：400-821-1215；青少年心理咨询热线：12355。紧急情况：拨打120或110；前往最近医院急诊科；联系学校心理老师或辅导员。记住：寻求帮助是勇敢的表现，你的生命非常宝贵！无论现在感到多么绝望，总有可以帮助你的人。不要独自承受，请立即联系专业人士。', 'crisis', '危机,热线,求助,干预,心理危机', '系统内置'),
('焦虑症识别与应对', '焦虑症是一种常见的心理障碍，表现为过度、持续的担忧和紧张。主要症状包括：坐立不安、容易疲劳、注意力难以集中、易怒、肌肉紧张、睡眠障碍。应对方法：1. 深呼吸练习：4-7-8呼吸法（吸气4秒，屏息7秒，呼气8秒）；2. 渐进式肌肉放松：从头到脚逐个部位放松；3. 正念冥想：专注当下，观察呼吸；4. 规律运动：每周3-5次有氧运动；5. 限制咖啡因摄入；6. 寻求专业帮助：心理咨询或药物治疗。', 'anxiety', '焦虑,紧张,担忧,坐立不安,焦虑症', '系统内置'),
('抑郁症症状与干预', '抑郁症是一种严重的心理疾病，主要症状包括：持续的情绪低落、兴趣丧失、精力减退、自我评价过低、睡眠障碍、食欲改变、注意力下降、反复出现死亡念头。干预措施：1. 心理治疗：认知行为疗法（CBT）、人际心理治疗；2. 药物治疗：抗抑郁药物需在医生指导下使用；3. 生活方式调整：规律作息、适度运动、社交活动；4. 危机干预：如有自杀念头，立即拨打心理援助热线400-161-9995；5. 家庭支持：家人理解和陪伴非常重要。', 'depression', '抑郁,低落,绝望,无望,抑郁症', '系统内置'),
('失眠改善方法', '失眠是指难以入睡、睡眠质量差或早醒。改善方法：1. 睡眠卫生：保持规律作息，每天同一时间睡觉和起床；2. 睡眠环境：安静、黑暗、温度适宜（18-22℃）；3. 睡前准备：避免咖啡因和酒精，不使用电子设备，温水泡脚；4. 放松技巧：腹式呼吸、渐进式肌肉放松、冥想；5. 刺激控制：床只用于睡觉，睡不着就起来；6. 睡眠限制：减少在床上的时间，提高睡眠效率；7. 如果持续失眠超过2周，建议就医。', 'insomnia', '失眠,睡眠,睡不着,早醒,睡眠障碍', '系统内置'),
('压力管理技巧', '压力是现代生活中常见的问题，有效管理方法：1. 时间管理：使用待办事项清单，设定优先级，学会拒绝；2. 认知重构：改变对压力源的看法，寻找积极意义；3. 放松训练：深呼吸、冥想、瑜伽、太极；4. 运动：每周至少150分钟中等强度运动；5. 社交支持：与朋友家人交流，寻求帮助；6. 兴趣爱好：培养能带来愉悦感的活动；7. 专业帮助：如果压力严重影响生活，寻求心理咨询。', 'stress', '压力,紧张,忙碌,焦虑,压力管理', '系统内置')
ON CONFLICT DO NOTHING;

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为需要的表创建触发器
DROP TRIGGER IF EXISTS update_user_updated_at ON "user";
CREATE TRIGGER update_user_updated_at BEFORE UPDATE ON "user" FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_alert_record_updated_at ON alert_record;
CREATE TRIGGER update_alert_record_updated_at BEFORE UPDATE ON alert_record FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_knowledge_document_updated_at ON knowledge_document;
CREATE TRIGGER update_knowledge_document_updated_at BEFORE UPDATE ON knowledge_document FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
