代码审阅汇总
本次工作区变更实现了 Gaokao（高考）分析 Spring AI/Qdrant 索引流水线，涉及 22 个文件、+1381/-67 行。以下是按严重程度排列的审阅结论：

P0 严重问题（需立即修复）
#	问题	文件	影响
1	InternalCorpusController.updatePaperIndex() 缺少 @Transactional，删除+批量插入不在事务内	gaokao-corpus-service InternalCorpusController	部分失败导致数据不一致
2	MaterializationServiceImpl 字段名错误：getKnowledgePathJson() 应为 getKnowledgeTagsJson()	gaokao-corpus-service MaterializationServiceImpl	物化时传 null，丢失知识标签
3	GaokaoQuestionCreateService XML/Text 混淆：stemXml 存入 stemText 字段	question-service GaokaoQuestionCreateService	题干显示包含 XML 标记
4	VectorServiceImpl RestClient 未关闭，存在资源泄漏	gaokao-analysis-service VectorServiceImpl	连接池耗尽
5	ensureCollection() Check-then-act 竞态条件	gaokao-analysis-service VectorServiceImpl	并发创建集合异常
P1 高风险问题
#	问题	文件	影响
6	DraftServiceImpl.buildStemXml() 中 ]]> 未转义，存在 XML 注入	gaokao-corpus-service DraftServiceImpl	安全漏洞
7	向量写入后立即搜索，Qdrant 可能尚未索引	gaokao-analysis-service Consumer	推荐结果不完整
8	Token 估算 length/4 对中文严重低估	gaokao-analysis-service Consumer	RAG chunk 元数据错误
9	IngestServiceImpl.calculateSha256() 和 encodeFileAsBase64() 全文件读入内存	gaokao-corpus-service IngestServiceImpl	大 PDF 导致 OOM
10	OCR 请求无 Base64 大小限制	gaokao-corpus-service OcrRecognizeRequest	内存耗尽攻击面
11	PublishServiceImpl.publishPaper() 硬编码 subjectCode = "MATH"	gaokao-corpus-service PublishServiceImpl	非数学科目无法发布
12	PublishServiceImpl 发布前无状态校验	gaokao-corpus-service PublishServiceImpl	可发布未完成草稿
13	RabbitMQ 缺少死信队列（DLQ）	gaokao-analysis-service RabbitTopologyConfig	消费失败消息丢失
P2 中等问题
#	问题	文件
14	OCR DTO 在 ocr-service 和 corpus-service 各定义一份，未放 internal-api-contract	多处
15	InternalQuestionController 新端点缺少 @Valid	question-service
16	AI 分析 JSON 反序列化异常被静默吞掉	gaokao-analysis-service AiAnalysisServiceImpl
17	Prompt 注入风险：用户输入直接嵌入 LLM prompt 无转义	gaokao-analysis-service AiAnalysisServiceImpl
18	searchSimilar() 接受 filters 参数但从未使用	gaokao-analysis-service VectorServiceImpl
19	Feign 调用无重试/熔断配置	gaokao-analysis-service、gaokao-corpus-service
20	MaterializationServiceImpl.parseJsonTokens() 手工解析 JSON 不安全	gaokao-corpus-service
设计观察
整体流水线设计合理：Publish → MQ → Index → Callback 的异步架构正确
common-contract 中的事件/回调契约结构清晰，序列化测试覆盖到位
GaokaoQuestionCreateService 有 @Transactional 且错误处理完整