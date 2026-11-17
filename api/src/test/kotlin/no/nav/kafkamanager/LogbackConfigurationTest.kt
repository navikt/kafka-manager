package no.nav.kafkamanager

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.util.LogbackMDCAdapter
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.status.Status
import no.nav.common.log.MaskingAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path

class LogbackConfigurationTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "01419012345|***********",
            "Foedselsnummer: 01419012345 mottatt|Foedselsnummer: *********** mottatt",
            """{"fnr":"01419012345"}|{"fnr":"***********"}""",
            "01419012345,02429054321|***********,***********",
        ],
    )
    fun `Maskerer 11 sifre i faktisk JSON-logg`(message: String, expected: String) {
        val output = captureLog { it.info(message) }

        assertEquals(expected, JsonMapper.builder().build().readTree(output)["message"].stringValue())
        assertFalse(output.contains("01419012345"))
        assertFalse(output.contains("02429054321"))
    }

    @Test
    fun `Maskerer 11 sifre i loggargumenter`() {
        val output = captureLog { it.info("Foedselsnummer: {} og {}", "01419012345", "02429054321") }

        assertEquals(
            "Foedselsnummer: *********** og ***********",
            JsonMapper.builder().build().readTree(output)["message"].stringValue(),
        )
        assertFalse(output.contains("01419012345"))
        assertFalse(output.contains("02429054321"))
    }

    @Test
    fun `Maskerer 11 sifre i MDC`() {
        val output = captureLog {
            it.loggerContext.mdcAdapter.put("fnr", "01419012345")
            it.info("Melding med MDC")
        }

        val json = JsonMapper.builder().build().readTree(output)
        assertEquals("Melding med MDC", json["message"].stringValue())
        assertEquals("***********", json["fnr"].stringValue())
        assertFalse(output.contains("01419012345"))
    }

    @Test
    fun `Maskerer 11 sifre i exception og underliggende aarsak`() {
        val output = captureLog {
            it.error(
                "Kunne ikke behandle melding",
                IllegalStateException("Feil for 01419012345", IllegalArgumentException("Ugyldig 02429054321")),
            )
        }

        val json = JsonMapper.builder().build().readTree(output)
        assertEquals("Kunne ikke behandle melding", json["message"].stringValue())
        val stackTrace = json["stack_trace"].stringValue()
        assertTrue(stackTrace.contains("IllegalStateException: Feil for ***********"))
        assertTrue(stackTrace.contains("IllegalArgumentException: Ugyldig ***********"))
        assertFalse(output.contains("01419012345"))
        assertFalse(output.contains("02429054321"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0141901234", "014190123456"])
    fun `Beholder tall med 10 og 12 sifre`(number: String) {
        val output = captureLog { it.info("Tall: {}", number) }

        assertEquals("Tall: $number", JsonMapper.builder().build().readTree(output)["message"].stringValue())
    }

    private fun captureLog(log: (Logger) -> Unit): String {
        val output = ByteArrayOutputStream()
        val context = LoggerContext().apply { mdcAdapter = LogbackMDCAdapter() }
        try {
            JoranConfigurator().apply { setContext(context) }
                .doConfigure(Path.of("src/main/resources/logback.xml").toFile())
            val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
            root.level = Level.INFO
            val maskingAppender = assertInstanceOf(MaskingAppender::class.java, root.getAppender("stdout_json_masked"))
            val consoleAppender = assertInstanceOf(
                ConsoleAppender::class.java,
                maskingAppender.getAppender("consoleAppender"),
            )
            consoleAppender.outputStream = output

            log(root)
        } finally {
            // MaskingAppender er asynkron; stopp venter til loggen er skrevet.
            context.stop()
        }
        return output.toString(UTF_8)
    }

    @Test
    fun `Laster lokal loggkonfigurasjon uten advarsler og beholder maskering`() {
        // Arrange
        val context = LoggerContext()
        val configurator = JoranConfigurator().apply { setContext(context) }

        try {
            // Act
            configurator.doConfigure(Path.of("src/main/resources/logback.xml").toFile())

            // Assert
            val problems = context.statusManager.copyOfStatusList.filter { it.level >= Status.WARN }
            assertTrue(problems.isEmpty()) { problems.joinToString("\n") { it.toString() } }
            val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
            val maskedAppender = root.getAppender("stdout_json_masked")
            assertInstanceOf(MaskingAppender::class.java, maskedAppender)
            assertTrue(maskedAppender.isStarted)
            assertEquals(Level.ERROR, context.getLogger("org.apache.cxf").effectiveLevel)
            assertEquals(Level.ERROR, context.getLogger("no.nav.common.cxf").effectiveLevel)
            assertEquals(Level.OFF, context.getLogger("org.apache.tomcat.util.http.parser.Cookie").effectiveLevel)
        } finally {
            context.stop()
        }
    }
}
