package tools.descartes.petsupplystore.kieker.rabbitmq;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import kieker.analysis.AnalysisController;
import kieker.analysis.IAnalysisController;
import kieker.analysis.IProjectContext;
import kieker.analysis.exception.AnalysisConfigurationException;
import kieker.analysis.plugin.annotation.InputPort;
import kieker.analysis.plugin.annotation.Plugin;
import kieker.analysis.plugin.filter.AbstractFilterPlugin;
import kieker.analysis.plugin.reader.amqp.AmqpReader;
import kieker.analysis.plugin.reader.amqp.ChunkingAmqpReader;
import kieker.analysis.plugin.reader.newio.RawDataReaderPlugin;
import kieker.common.configuration.Configuration;
import kieker.common.record.IMonitoringRecord;
import kieker.common.record.system.CPUUtilizationRecord;
import kieker.common.record.system.MemSwapUsageRecord;
import kieker.tools.util.LoggingTimestampConverter;

@WebServlet("/logs")
public class DisplayLogs extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final String URI = "amqp://admin:nimda@127.0.0.1";
	private static final String QUEUENAME = "kieker";

	/**
	 * {@inheritDoc}
	 * 
	 * @throws IOException
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		final IAnalysisController analysisInstance = new AnalysisController();
		Configuration configuration = new Configuration();

		configuration.setProperty(RawDataReaderPlugin.CONFIG_PROPERTY_READER, "kieker.analysis.plugin.reader.amqp.ChunkingAmqpReader");
		configuration.setProperty(RawDataReaderPlugin.CONFIG_PROPERTY_DESERIALIZER, "kieker.analysis.plugin.reader.newio.deserializer.BinaryDeserializer");

		configuration.setProperty(ChunkingAmqpReader.CONFIG_PROPERTY_URI, URI);
		configuration.setProperty(ChunkingAmqpReader.CONFIG_PROPERTY_QUEUENAME, QUEUENAME);

		RawDataReaderPlugin reader = new RawDataReaderPlugin(configuration, analysisInstance);
        final StdOutDumpConsumer consumer = new StdOutDumpConsumer(new Configuration(), analysisInstance);
 
        try {
            analysisInstance.connect(reader, AmqpReader.OUTPUT_PORT_NAME_RECORDS, consumer,
                    StdOutDumpConsumer.INPUT_PORT_NAME);
            analysisInstance.run();
        } catch (final AnalysisConfigurationException e) {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            final String sStackTrace = sw.toString();
            throw new IllegalStateException(sStackTrace);
        }

	}

	@Plugin
	class StdOutDumpConsumer extends AbstractFilterPlugin {

		public static final String INPUT_PORT_NAME = "newMonitoringRecord";

		public StdOutDumpConsumer(final Configuration configuration, final IProjectContext projectContext) {
			super(configuration, projectContext);
		}

		@InputPort(name = StdOutDumpConsumer.INPUT_PORT_NAME, eventTypes = { IMonitoringRecord.class })
		public void newMonitoringRecord(final Object record) {
			if (record instanceof CPUUtilizationRecord) {
				final CPUUtilizationRecord cpuUtilizationRecord = (CPUUtilizationRecord) record;

				final String hostname = cpuUtilizationRecord.getHostname();
				final String cpuId = cpuUtilizationRecord.getCpuID();
				final double utilizationPercent = cpuUtilizationRecord.getTotalUtilization() * 100;

				System.out.println(String.format(
						"%s: [CPU] host: %s ; cpu-id: %s ; utilization: %3.2f %%", LoggingTimestampConverter
								.convertLoggingTimestampToUTCString(cpuUtilizationRecord.getTimestamp()),
						hostname, cpuId, utilizationPercent));
			} else if (record instanceof MemSwapUsageRecord) {
				final MemSwapUsageRecord memSwapUsageRecord = (MemSwapUsageRecord) record;

				final String hostname = memSwapUsageRecord.getHostname();
				final double memUsageMB = memSwapUsageRecord.getMemUsed() / (1024d * 1024d);
				final double swapUsageMB = memSwapUsageRecord.getSwapUsed() / (1024d * 1024d);

				System.out.println(String.format("%s: [Mem/Swap] host: %s ; mem usage: %s MB ; swap usage: %s MB",
						LoggingTimestampConverter.convertLoggingTimestampToUTCString(memSwapUsageRecord.getTimestamp()),
						hostname, memUsageMB, swapUsageMB));
			} // else Unexpected record type
		}

		@Override
		public Configuration getCurrentConfiguration() {
			return new Configuration();
		}
	}

}