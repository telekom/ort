package org.ossreviewtoolkit.reporter.reporters.osCakeReporterModel


data class CompoundLicense(val expression: String) {
    val left: String = ""
    init {
        println("Compound License to process: $this")
    }

    override fun toString(): String = expression

}
