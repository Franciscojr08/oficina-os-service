package br.com.oficina.os.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Roda os cenários de {@code src/test/resources/features} junto com os demais testes do
 * {@code ./mvnw verify} (e, portanto, no CI). Relatório HTML em {@code target/cucumber/relatorio.html}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "br.com.oficina.os.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
		value = "pretty, html:target/cucumber/relatorio.html, junit:target/cucumber/relatorio.xml")
class CenariosBddTest {
}
