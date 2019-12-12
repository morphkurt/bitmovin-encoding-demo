package xyz.damitha.bitmovin;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.bitmovin.api.sdk.BitmovinApi;
import com.bitmovin.api.sdk.common.BitmovinException;
import com.bitmovin.api.sdk.model.AacAudioConfiguration;
import com.bitmovin.api.sdk.model.AclEntry;
import com.bitmovin.api.sdk.model.AclPermission;
import com.bitmovin.api.sdk.model.CodecConfiguration;
import com.bitmovin.api.sdk.model.DashManifest;
import com.bitmovin.api.sdk.model.DashManifestDefault;
import com.bitmovin.api.sdk.model.DashManifestDefaultVersion;
import com.bitmovin.api.sdk.model.Encoding;
import com.bitmovin.api.sdk.model.EncodingOutput;
import com.bitmovin.api.sdk.model.Fmp4Muxing;
import com.bitmovin.api.sdk.model.H264VideoConfiguration;
import com.bitmovin.api.sdk.model.HlsManifest;
import com.bitmovin.api.sdk.model.HlsManifestDefault;
import com.bitmovin.api.sdk.model.HlsManifestDefaultVersion;
import com.bitmovin.api.sdk.model.HttpInput;
import com.bitmovin.api.sdk.model.Input;
import com.bitmovin.api.sdk.model.MessageType;
import com.bitmovin.api.sdk.model.MuxingStream;
import com.bitmovin.api.sdk.model.Output;
import com.bitmovin.api.sdk.model.PresetConfiguration;
import com.bitmovin.api.sdk.model.S3Output;
import com.bitmovin.api.sdk.model.StartEncodingRequest;
import com.bitmovin.api.sdk.model.Status;
import com.bitmovin.api.sdk.model.Stream;
import com.bitmovin.api.sdk.model.StreamInput;
import com.bitmovin.api.sdk.model.StreamSelectionMode;
import com.bitmovin.api.sdk.model.Task;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import feign.Logger.Level;
import feign.slf4j.Slf4jLogger;

/**
 * Hello world!
 */
public final class noDRM {

    private static BitmovinApi bitmovinApi;
    private static final Logger logger = LoggerFactory.getLogger(noDRM.class);

    private noDRM() {
    }

    public static void main(String[] args) {

        Options options = createOptions();

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
            String api_key = cmd.getOptionValue("api-key");

            /*
             * Initialize the Bitmovin API
             */

            bitmovinApi = BitmovinApi.builder().withApiKey(api_key).withLogger(new Slf4jLogger(), Level.BASIC).build();

            /*
             * Create the HTTPs input host
             */

            // create the input

            Encoding encoding = createEncoding("Multiple FMP4 with DASH and HLS", "Encoding with multiple fMP4 muxings");

            Output output = createS3Output(cmd.getOptionValue("s3-bucket"), cmd.getOptionValue("s3-access-key"),
                    cmd.getOptionValue("s3-secret-key"));

            String inputFilePath = cmd.getOptionValue("input-path");

            HttpInput input = createHttpInput(cmd.getOptionValue("input-host"));

            final List<H264VideoConfiguration> videoConfigurations = Arrays.asList(
                    createH264VideoConfig(1080, 4_800_000L), createH264VideoConfig(720, 2_400_000L),
                    createH264VideoConfig(480, 1_200_000L), createH264VideoConfig(360, 800_000L),
                    createH264VideoConfig(240, 400_000L));

            // Create a common AAC audio stream for all muxings
            AacAudioConfiguration aacConfig = createAacAudioConfig();
            Stream audioStream = createStream(encoding, input, inputFilePath, aacConfig);

            // Create a video stream and a progressive MP4 muxing per video codec
            // configuration
            for (H264VideoConfiguration videoConfiguration : videoConfigurations) {
                Stream videoStream = createStream(encoding, input, inputFilePath, videoConfiguration);
                createFmp4Muxing(encoding, output, videoConfiguration.getHeight().toString(), videoStream);
            }

            createFmp4Muxing(encoding, output, "audio", audioStream);

            executeEncoding(encoding);

            generateDashManifest(encoding, output, "/");
            generateHlsManifest(encoding, output, "/");

        } catch (Exception e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
        }

    }

    private static Fmp4Muxing createFmp4Muxing(Encoding encoding, Output output, String outputPath, Stream stream)
            throws BitmovinException {

        Fmp4Muxing muxing = new Fmp4Muxing();
        muxing.addOutputsItem(buildEncodingOutput(output, outputPath));

        muxing.setSegmentLength(4.0);

        MuxingStream muxingStream = new MuxingStream();
        muxingStream.setStreamId(stream.getId());
        muxing.addStreamsItem(muxingStream);

        return bitmovinApi.encoding.encodings.muxings.fmp4.create(encoding.getId(), muxing);
    }

    private static void generateHlsManifest(Encoding encoding, Output output, String outputPath) throws Exception {
        HlsManifestDefault hlsManifestDefault = new HlsManifestDefault();
        hlsManifestDefault.setEncodingId(encoding.getId());
        hlsManifestDefault.addOutputsItem(buildEncodingOutput(output, outputPath));
        hlsManifestDefault.setName("master.m3u8");
        hlsManifestDefault.setVersion(HlsManifestDefaultVersion.V1);

        hlsManifestDefault = bitmovinApi.encoding.manifests.hls.defaultapi.create(hlsManifestDefault);
        executeHlsManifestCreation(hlsManifestDefault);
    }

    private static void generateDashManifest(Encoding encoding, Output output, String outputPath) throws Exception {
        DashManifestDefault dashManifestDefault = new DashManifestDefault();
        dashManifestDefault.setEncodingId(encoding.getId());
        dashManifestDefault.setManifestName("stream.mpd");
        dashManifestDefault.setVersion(DashManifestDefaultVersion.V1);
        dashManifestDefault.addOutputsItem(buildEncodingOutput(output, outputPath));
        dashManifestDefault = bitmovinApi.encoding.manifests.dash.defaultapi.create(dashManifestDefault);
        executeDashManifestCreation(dashManifestDefault);
    }

    private static void executeHlsManifestCreation(HlsManifest hlsManifest)
            throws BitmovinException, InterruptedException {

        bitmovinApi.encoding.manifests.hls.start(hlsManifest.getId());

        Task task;
        do {
            Thread.sleep(1000);
            task = bitmovinApi.encoding.manifests.hls.status(hlsManifest.getId());
        } while (task.getStatus() != Status.FINISHED && task.getStatus() != Status.ERROR);

        if (task.getStatus() == Status.ERROR) {
            logTaskErrors(task);
            throw new RuntimeException("HLS manifest creation failed");
        }
        logger.info("HLS manifest creation finished successfully");
    }

    /**
     * Starts the DASH manifest creation and periodically polls its status until it
     * reaches a final state
     *
     * <p>
     * API endpoints:
     * https://bitmovin.com/docs/encoding/api-reference/sections/manifests#/Encoding/PostEncodingManifestsDashStartByManifestId
     * https://bitmovin.com/docs/encoding/api-reference/sections/manifests#/Encoding/GetEncodingManifestsDashStatusByManifestId
     *
     * @param dashManifest The DASH manifest to be created
     */
    private static void executeDashManifestCreation(DashManifest dashManifest)
            throws BitmovinException, InterruptedException {
        bitmovinApi.encoding.manifests.dash.start(dashManifest.getId());

        Task task;
        do {
            Thread.sleep(1000);
            task = bitmovinApi.encoding.manifests.dash.status(dashManifest.getId());
        } while (task.getStatus() != Status.FINISHED && task.getStatus() != Status.ERROR);

        if (task.getStatus() == Status.ERROR) {
            logTaskErrors(task);
            throw new RuntimeException("DASH manifest creation failed");
        }
        logger.info("DASH manifest creation finished successfully");
    }

    private static HttpInput createHttpInput(String host) throws BitmovinException {
        HttpInput input = new HttpInput();
        input.setHost(host);

        return bitmovinApi.encoding.inputs.http.create(input);
    }

    private static void executeEncoding(Encoding encoding) throws InterruptedException, BitmovinException {
        bitmovinApi.encoding.encodings.start(encoding.getId(), new StartEncodingRequest());

        Task task;
        do {
            Thread.sleep(5000);
            task = bitmovinApi.encoding.encodings.status(encoding.getId());
            logger.info("encoding status is {} (progress: {} %)", task.getStatus(), task.getProgress());
        } while (task.getStatus() != Status.FINISHED && task.getStatus() != Status.ERROR);

        if (task.getStatus() == Status.ERROR) {
            logTaskErrors(task);
            throw new RuntimeException("Encoding failed");
        }
        logger.info("encoding finished successfully");
    }

    private static void logTaskErrors(Task task) {
        task.getMessages().stream().filter(msg -> msg.getType() == MessageType.ERROR)
                .forEach(msg -> logger.error(msg.getText()));
    }

    public static String buildAbsolutePath(String basePath, String relativePath) {

        return Paths.get(basePath, "noDRM", relativePath).toString();
    }

    private static EncodingOutput buildEncodingOutput(Output output, String outputPath) {
        AclEntry aclEntry = new AclEntry();
        aclEntry.setPermission(AclPermission.PUBLIC_READ);

        EncodingOutput encodingOutput = new EncodingOutput();
        encodingOutput.setOutputPath(buildAbsolutePath("usecase1-nodrm", outputPath));
        encodingOutput.setOutputId(output.getId());
        encodingOutput.addAclItem(aclEntry);
        return encodingOutput;
    }

    private static Stream createStream(Encoding encoding, Input input, String inputPath,
            CodecConfiguration codecConfiguration) throws BitmovinException {
        StreamInput streamInput = new StreamInput();
        streamInput.setInputId(input.getId());
        streamInput.setInputPath(inputPath);
        streamInput.setSelectionMode(StreamSelectionMode.AUTO);

        Stream stream = new Stream();
        stream.addInputStreamsItem(streamInput);
        stream.setCodecConfigId(codecConfiguration.getId());

        return bitmovinApi.encoding.encodings.streams.create(encoding.getId(), stream);
    }

    private static AacAudioConfiguration createAacAudioConfig() throws BitmovinException {
        AacAudioConfiguration config = new AacAudioConfiguration();
        config.setName("AAC 128 kbit/s");
        config.setBitrate(128_000L);

        return bitmovinApi.encoding.configurations.audio.aac.create(config);
    }

    private static H264VideoConfiguration createH264VideoConfig(int height, long bitrate) throws BitmovinException {
        H264VideoConfiguration config = new H264VideoConfiguration();
        config.setName(String.format("H.264 %dp", height));
        config.setPresetConfiguration(PresetConfiguration.VOD_STANDARD);
        config.setHeight(height);
        config.setBitrate(bitrate);

        return bitmovinApi.encoding.configurations.video.h264.create(config);
    }

    private static Encoding createEncoding(String name, String description) throws BitmovinException {
        Encoding encoding = new Encoding();
        encoding.setName(name);
        encoding.setDescription(description);

        return bitmovinApi.encoding.encodings.create(encoding);
    }

    private static S3Output createS3Output(String bucketName, String accessKey, String secretKey)
            throws BitmovinException {

        S3Output s3Output = new S3Output();
        s3Output.setBucketName(bucketName);
        s3Output.setAccessKey(accessKey);
        s3Output.setSecretKey(secretKey);

        return bitmovinApi.encoding.outputs.s3.create(s3Output);
    }

    private static Options createOptions() {
        Options options = new Options();

        Option input = new Option("k", "api-key", true, "Bitmovin API Key");
        input.setRequired(true);
        options.addOption(input);

        Option s3Bucket = new Option("sb", "s3-bucket", true, "s3 Bucket");
        s3Bucket.setRequired(true);
        options.addOption(s3Bucket);

        Option s3AccessKey = new Option("sak", "s3-access-key", true, "s3 Access Key");
        s3AccessKey.setRequired(true);
        options.addOption(s3AccessKey);

        Option s3SecretKey = new Option("ssk", "s3-secret-key", true, "s3 Secret Key");
        s3SecretKey.setRequired(true);
        options.addOption(s3SecretKey);

        Option inputHost = new Option("ih", "input-host", true, "input Host");
        inputHost.setRequired(true);
        options.addOption(inputHost);

        Option inputPath = new Option("ip", "input-path", true, "input Path");
        inputPath.setRequired(true);
        options.addOption(inputPath);

        return options;
    }
}
