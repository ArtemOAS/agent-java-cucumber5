package com.epam.reportportal.cucumber;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import io.cucumber.core.internal.gherkin.ast.Location;
import io.cucumber.core.internal.gherkin.ast.Step;
import io.cucumber.plugin.event.Argument;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.StepArgument;
import io.reactivex.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rp.com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
public class ParameterTest {

	private TestStepReporter stepReporter;

	@Mock
	private ReportPortalClient reportPortalClient;

	@Mock
	private ListenerParameters listenerParameters;

	@Mock
	private PickleStepTestStep testStep;

	@Before
	public void initLaunch() {
		MockitoAnnotations.initMocks(this);
		when(listenerParameters.getEnable()).thenReturn(true);
		when(listenerParameters.getBaseUrl()).thenReturn("http://example.com");
		when(listenerParameters.getIoPoolSize()).thenReturn(10);
		when(listenerParameters.getBatchLogsSize()).thenReturn(5);
		when(reportPortalClient.startTestItem(any())).thenAnswer((i) -> CommonUtils.createMaybe(new ItemCreatedRS(CommonUtils.generateUniqueId(),
				CommonUtils.generateUniqueId()
		)));
		when(reportPortalClient.startTestItem(
				anyString(),
				any()
		)).thenAnswer((i) -> CommonUtils.createMaybe(new ItemCreatedRS(CommonUtils.generateUniqueId(), CommonUtils.generateUniqueId())));
		stepReporter = new TestStepReporter() {
			@Override
			protected ReportPortal buildReportPortal() {
				return ReportPortal.create(reportPortalClient, listenerParameters);
			}
		};

	}

	@Test
	public void verifyClientRetrievesParametersFromRequest() {
		when(reportPortalClient.startLaunch(any(StartLaunchRQ.class))).then(t -> Maybe.create(emitter -> {
			StartLaunchRS rs = new StartLaunchRS();
			rs.setId("launchId");
			emitter.onSuccess(rs);
			emitter.onComplete();
		}).cache());

		stepReporter.beforeLaunch();

		ArrayList<String> parameterValues = Lists.newArrayList("1", "parameter");
		ArrayList<String> parameterNames = Lists.newArrayList("count", "item");

		when(testStep.getStep()).thenReturn(new TestStep());
		when(testStep.getDefinitionArgument()).thenReturn(parameterValues.stream().map(this::getArgument).collect(Collectors.toList()));
		when(stepReporter.scenarioContext.getId()).thenReturn(Maybe.create(emitter -> {
			emitter.onSuccess("scenarioId");
			emitter.onComplete();
		}));
		when(stepReporter.scenarioContext.getStep(testStep)).thenReturn(new Step(new Location(1, 1),
				"keyword",
				String.format("test with parameters <%s>", String.join("> <", parameterNames)),
				null
		));
		when(stepReporter.scenarioContext.getStepPrefix()).thenReturn("");

		stepReporter.beforeStep(testStep);

		ArgumentCaptor<StartTestItemRQ> startTestItemRQArgumentCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(reportPortalClient, timeout(1000).times(1)).startTestItem(anyString(), startTestItemRQArgumentCaptor.capture());

		StartTestItemRQ request = startTestItemRQArgumentCaptor.getValue();
		assertNotNull(request);
		assertNotNull(request.getParameters());
		assertEquals(2, request.getParameters().size());
		assertTrue(request.getParameters()
				.stream()
				.allMatch(it -> parameterValues.contains(it.getValue()) && parameterNames.contains(it.getKey())));
	}

	private Argument getArgument(String it) {
		return new Argument() {
			@Override
			public String getValue() {
				return it;
			}

			@Override
			public int getStart() {
				return 0;
			}

			@Override
			public int getEnd() {
				return 0;
			}
		};
	}

	private class TestStep implements io.cucumber.plugin.event.Step {

		@Override
		public StepArgument getArgument() {
			return null;
		}

		@Override
		public String getKeyWord() {
			return "Given";
		}

		@Override
		public String getText() {
			return "test with parameters";
		}

		@Override
		public int getLine() {
			return 0;
		}
	}
}
