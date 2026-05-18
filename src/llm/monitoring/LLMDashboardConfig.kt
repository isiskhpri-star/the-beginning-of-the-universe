package llm.monitoring

/**
 * Recommended Datadog dashboard configuration for monitoring the background
 * LLM content-generation system. Use these definitions as a reference when
 * creating dashboards via the Datadog UI or Terraform `datadog_dashboard`
 * resources.
 */
object LLMDashboardConfig {

    /**
     * Returns the recommended dashboard widget definitions as structured maps.
     */
    fun getRecommendedWidgets(): List<Map<String, Any>> = listOf(
        // -- Throughput --
        mapOf(
            "title" to "Generation Requests / min",
            "type" to "timeseries",
            "query" to "sum:llm.engine.generation.count{*}.as_rate()"
        ),
        mapOf(
            "title" to "Success vs Failed / min",
            "type" to "timeseries",
            "queries" to listOf(
                "sum:llm.engine.generation.success{*}.as_rate()",
                "sum:llm.engine.generation.failed{*}.as_rate()"
            )
        ),

        // -- Latency --
        mapOf(
            "title" to "Generation Latency (p50 / p95 / p99)",
            "type" to "timeseries",
            "queries" to listOf(
                "avg:llm.engine.generation.duration_ms{*}",
                "percentile:llm.engine.generation.duration_ms{*} by {none} p:95",
                "percentile:llm.engine.generation.duration_ms{*} by {none} p:99"
            )
        ),

        // -- Content type breakdown --
        mapOf(
            "title" to "Generations by Content Type",
            "type" to "toplist",
            "query" to "sum:llm.engine.generation.count{*} by {content_type}.as_count()"
        ),

        // -- Search quality --
        mapOf(
            "title" to "Search Results per Request",
            "type" to "distribution",
            "query" to "llm.engine.search.result_count{*} by {content_type}"
        ),
        mapOf(
            "title" to "Filtered Results per Request",
            "type" to "distribution",
            "query" to "llm.engine.search.filtered_count{*} by {content_type}"
        ),

        // -- Confidence --
        mapOf(
            "title" to "Content Confidence Distribution",
            "type" to "distribution",
            "query" to "llm.engine.content.confidence{*} by {content_type}"
        ),

        // -- Queue health --
        mapOf(
            "title" to "Scheduler Queue Depth",
            "type" to "timeseries",
            "query" to "max:llm.engine.scheduler.queue_depth{*}"
        ),

        // -- Index health --
        mapOf(
            "title" to "Total Indexed Entries",
            "type" to "query_value",
            "query" to "max:llm.engine.index.entry_count{*}"
        )
    )

    /**
     * Returns recommended Datadog monitor definitions for alerting.
     */
    fun getRecommendedMonitors(): List<Map<String, Any>> = listOf(
        mapOf(
            "name" to "[LLM Engine] High Failure Rate",
            "type" to "metric alert",
            "query" to "sum(last_5m):sum:llm.engine.generation.failed{*}.as_count() / " +
                "sum:llm.engine.generation.count{*}.as_count() > 0.3",
            "message" to "More than 30% of LLM generation requests failed in the last " +
                "5 minutes. Check data source availability and LLM provider health. " +
                "@slack-alerts",
            "priority" to 2
        ),
        mapOf(
            "name" to "[LLM Engine] Queue Depth Critical",
            "type" to "metric alert",
            "query" to "max(last_5m):max:llm.engine.scheduler.queue_depth{*} > 500",
            "message" to "Scheduler queue depth exceeds 500. Generation throughput may " +
                "be insufficient. Consider scaling or reducing request volume. " +
                "@pagerduty-oncall",
            "priority" to 1
        ),
        mapOf(
            "name" to "[LLM Engine] Generation Latency Spike",
            "type" to "metric alert",
            "query" to "percentile(last_10m):p95:llm.engine.generation.duration_ms{*} > 10000",
            "message" to "P95 generation latency exceeds 10 seconds. LLM provider may " +
                "be degraded. @slack-alerts",
            "priority" to 2
        ),
        mapOf(
            "name" to "[LLM Engine] Low Confidence Output",
            "type" to "metric alert",
            "query" to "avg(last_15m):avg:llm.engine.content.confidence{*} < 20",
            "message" to "Average content confidence below 20% in the last 15 minutes. " +
                "Data sources may need re-indexing or search relevance tuning. " +
                "@slack-alerts",
            "priority" to 3
        ),
        mapOf(
            "name" to "[LLM Engine] No Generations (Dead Service)",
            "type" to "metric alert",
            "query" to "sum(last_15m):sum:llm.engine.generation.count{*}.as_count() < 1",
            "message" to "No generation requests processed in 15 minutes. The LLM engine " +
                "may be down. @pagerduty-oncall",
            "priority" to 1
        )
    )
}
