package transaction.monitoring

/**
 * Recommended Datadog dashboard configuration for monitoring the transaction
 * safety system. This file documents the widgets, queries, and monitors that
 * should be set up in your Datadog organization.
 *
 * Use these definitions as a reference when creating dashboards via the
 * Datadog UI or Terraform `datadog_dashboard` resources.
 */
object DatadogDashboardConfig {

    /**
     * Returns the recommended dashboard widget definitions as structured maps.
     * Each map can be serialized to JSON for the Datadog API.
     */
    fun getRecommendedWidgets(): List<Map<String, Any>> = listOf(
        // ── Throughput ──────────────────────────────────────────────────
        mapOf(
            "title" to "Transaction Evaluations / min",
            "type" to "timeseries",
            "query" to "sum:transaction.safety.evaluation.count{*}.as_rate()"
        ),
        mapOf(
            "title" to "Authorized vs Denied / min",
            "type" to "timeseries",
            "queries" to listOf(
                "sum:transaction.safety.evaluation.authorized{*}.as_rate()",
                "sum:transaction.safety.evaluation.denied{*}.as_rate()"
            )
        ),

        // ── Latency ─────────────────────────────────────────────────────
        mapOf(
            "title" to "Evaluation Latency (p50 / p95 / p99)",
            "type" to "timeseries",
            "queries" to listOf(
                "avg:transaction.safety.evaluation.duration_ms{*}",
                "percentile:transaction.safety.evaluation.duration_ms{*} by {none} p:95",
                "percentile:transaction.safety.evaluation.duration_ms{*} by {none} p:99"
            )
        ),

        // ── Denial breakdown ────────────────────────────────────────────
        mapOf(
            "title" to "Denials by Check Name",
            "type" to "toplist",
            "query" to "sum:transaction.safety.check.failed{*} by {check_name}.as_count()"
        ),

        // ── Rate limits ─────────────────────────────────────────────────
        mapOf(
            "title" to "Accounts Near Rate Limit",
            "type" to "toplist",
            "query" to "max:transaction.safety.rate_limit.current_count{*} by {account_id}"
        ),

        // ── Suspicious activity ─────────────────────────────────────────
        mapOf(
            "title" to "Suspicious Activity — Recent Denials by Account",
            "type" to "toplist",
            "query" to "max:transaction.safety.suspicious_activity.denials{*} by {account_id}"
        ),

        // ── Transaction amounts ─────────────────────────────────────────
        mapOf(
            "title" to "Transaction Amount Distribution",
            "type" to "distribution",
            "query" to "transaction.safety.transaction.amount{*} by {currency}"
        )
    )

    /**
     * Returns recommended Datadog monitor definitions for alerting.
     * Each map can be used with the Datadog API or Terraform `datadog_monitor`.
     */
    fun getRecommendedMonitors(): List<Map<String, Any>> = listOf(
        mapOf(
            "name" to "[Transaction Safety] High Denial Rate",
            "type" to "metric alert",
            "query" to "sum(last_5m):sum:transaction.safety.evaluation.denied{*}.as_count() / " +
                "sum:transaction.safety.evaluation.count{*}.as_count() > 0.5",
            "message" to "More than 50% of transactions denied in the last 5 minutes. " +
                "Check for misconfigured accounts or an attack. @slack-alerts",
            "priority" to 2
        ),
        mapOf(
            "name" to "[Transaction Safety] Suspicious Activity Triggered",
            "type" to "metric alert",
            "query" to "max(last_5m):max:transaction.safety.suspicious_activity.denials{*} " +
                "by {account_id} > 5",
            "message" to "Account {{account_id.name}} has triggered the suspicious-activity " +
                "auto-block. Investigate immediately. @pagerduty-oncall",
            "priority" to 1
        ),
        mapOf(
            "name" to "[Transaction Safety] Evaluation Latency Spike",
            "type" to "metric alert",
            "query" to "percentile(last_10m):p95:transaction.safety.evaluation.duration_ms{*} > 500",
            "message" to "P95 evaluation latency exceeds 500 ms. May indicate downstream " +
                "service degradation. @slack-alerts",
            "priority" to 3
        ),
        mapOf(
            "name" to "[Transaction Safety] No Evaluations (Dead Service)",
            "type" to "metric alert",
            "query" to "sum(last_15m):sum:transaction.safety.evaluation.count{*}.as_count() < 1",
            "message" to "No transaction evaluations in 15 minutes. The safety service " +
                "may be down. @pagerduty-oncall",
            "priority" to 1
        )
    )
}
