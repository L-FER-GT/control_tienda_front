package com.lfergt.controltienda.domain.model

enum class UsagePeriod { DAY, MONTH, TOTAL }

enum class UsageLevel { OK, WARNING, EXCEEDED }

/** Consumo de un recurso de Firebase frente a su cuota gratuita del plan Blaze. */
data class UsageMetric(
    val key: String,
    val label: String,
    val used: Double,
    val limit: Double,
    val unit: String,
    val period: UsagePeriod,
) {
    val ratio: Double get() = if (limit <= 0) 0.0 else used / limit

    val level: UsageLevel
        get() = when {
            ratio >= 1.0 -> UsageLevel.EXCEEDED
            ratio >= WARNING_RATIO -> UsageLevel.WARNING
            else -> UsageLevel.OK
        }

    companion object {
        /** Umbral de alerta: 80 % de la cuota gratuita. */
        const val WARNING_RATIO = 0.8
    }
}

data class UsageReport(
    val metrics: List<UsageMetric>,
    val generatedAt: Long,
    /** "monitoring" con datos reales de Cloud Monitoring, "emulator" con datos simulados. */
    val source: String,
)
