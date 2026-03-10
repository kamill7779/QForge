package io.github.kamill7779.qforge.common.contract;

/**
 * MQ 拓扑常量 —— gaokao 正式题索引构建通道。
 */
public final class GaokaoIndexingConstants {

    private GaokaoIndexingConstants() {
    }

    public static final String GAOKAO_INDEX_EXCHANGE = "qforge.gaokao.index";
    public static final String ROUTING_PAPER_INDEX_REQUESTED = "gaokao.paper.index.requested";
    public static final String PAPER_INDEX_REQUESTED_QUEUE = "qforge.gaokao.paper.index.requested.q";
    public static final String PAPER_INDEX_REQUESTED_DLQ = "qforge.gaokao.paper.index.requested.dlq";
    public static final String ROUTING_PAPER_INDEX_REQUESTED_DLQ = ROUTING_PAPER_INDEX_REQUESTED + ".dlq";
}
