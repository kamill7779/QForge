package io.github.kamill7779.qforge.common.contract;

/**
 * MQ 拓扑常量 —— 异步落库（DB write-back）通道。
 * <p>
 * 生产者（question-service / ocr-service）只需引用 {@link #DB_EXCHANGE} 和
 * {@link #ROUTING_DB_PERSIST} 即可投递；persist-service 负责声明完整拓扑
 * （exchange + queue + binding）并消费。
 */
public final class DbPersistConstants {

    private DbPersistConstants() { /* utility */ }

    /** DirectExchange 名称。 */
    public static final String DB_EXCHANGE = "qforge.db";

    /** 持久化队列名称。 */
    public static final String DB_PERSIST_QUEUE = "qforge.db.persist.q";

    /** 路由键。 */
    public static final String ROUTING_DB_PERSIST = "db.persist";
}
