# Bitmovin Java Encoding Example 

Include Bitmovin in your Maven dependecies as shown (here)[https://github.com/morphkurt/bitmovin-encoding-demo/blob/f7a308bb60579e51b10853d0f8d152ef0545d9c1/pom.xml#L32-L36]

## Non DRM Solution

### Build the Project

Clone the repo and build the project

```bash

mvn install
cd target

java -cp usecase1-1.0-SNAPSHOT-jar-with-dependencies.jar xyz.damitha.bitmovin.noDRM 
Missing required options: k, sb, sak, ssk, ih, ip
usage: utility-name
 -ih,--input-host <arg>       input Host
 -ip,--input-path <arg>       input Path
 -k,--api-key <arg>           Bitmovin API Key
 -sak,--s3-access-key <arg>   s3 Access Key
 -sb,--s3-bucket <arg>        s3 Bucket
 -ssk,--s3-secret-key <arg>   s3 Secret Key
```

High level flow of the encoding flow is covered here.

1) Initialization of the Bitmovin API
```java
bitmovinApi = BitmovinApi.builder().withApiKey(api_key).withLogger(new Slf4jLogger(), Level.BASIC).build();
```
2) Creating an encoding object
```java
Encoding encoding = createEncoding("Multiple FMP4 with DASH and HLS", "Encoding with multiple fMP4 muxings");
```
3) Create the S3 Bucket configration with access key and secret
```java
Output output = createS3Output(cmd.getOptionValue("s3-bucket"), cmd.getOptionValue("s3-access-key"),cmd.getOptionValue("s3-secret-key"));
```
4) Create HTTP input to read the source video file from URL
```java
String inputFilePath = cmd.getOptionValue("input-path");
HttpInput input = createHttpInput(cmd.getOptionValue("input-host"));
```
5) Create multiple H264 video renditions
```java
final List<H264VideoConfiguration> videoConfigurations = Arrays.asList(
     createH264VideoConfig(1080, 4_800_000L), createH264VideoConfig(720, 2_400_000L),
     createH264VideoConfig(480, 1_200_000L), createH264VideoConfig(360, 800_000L),
     createH264VideoConfig(240, 400_000L));
```
6) Create Audio and Multiple Video Streams
```java
 Stream videoStream = createStream(encoding, input, inputFilePath, videoConfiguration);
 Stream audioStream = createStream(encoding, input, inputFilePath, aacConfig);
```
7) Create Multiple fMP4 Muxing for each audio and video track (example)
```java
createFmp4Muxing(encoding, output, "audio", audioStream);
```
8) Execute Encoding
```java
executeEncoding(encoding);
```
9) Create DASH and HLS outputs
```java
generateDashManifest(encoding, output, "/");
generateHlsManifest(encoding, output, "/");
```



##  DRM Solution

### Build the Project

Clone the repo and build the project

```bash

mvn install
mvn package

java -cp usecase1-1.0-SNAPSHOT-jar-with-dependencies.jar xyz.damitha.bitmovin.DRM 
Missing required options: k, sb, sak, ssk, ih, ip
usage: utility-name
 -ck,--cenc-key <arg>         CENC Key
 -ckid,--cenc-key-id <arg>    CENC Key id
 -fiv,--fairplay-iv <arg>     Fairplay IV
 -furi,--fairplay-uri <arg>   Fairplay URI
 -ih,--input-host <arg>       input Host
 -ip,--input-path <arg>       input Path
 -k,--api-key <arg>           Bitmovin API Key
 -sak,--s3-access-key <arg>   s3 Access Key
 -sb,--s3-bucket <arg>        s3 Bucket
 -ssk,--s3-secret-key <arg>   s3 Secret Key
 -wvp,--widevine-pssh <arg>   Widevine PSSH
```

High level flow of the encoding flow is same as nonDRM however extra step is required to create CENC encryption

Each video and audio muxer needs to be passed encryption process. Which requires few parameters. These parameters needs to confirmed with DRM providers.

```java
CencDrm cencDrm = new CencDrm();
cencDrm.addOutputsItem(buildEncodingOutput(output, outputPath));
cencDrm.setKey(cmd.getOptionValue("cenc-key"));
cencDrm.setKid(cmd.getOptionValue("cenc-key-id"));

CencWidevine widevineDrm = new CencWidevine();
widevineDrm.setPssh(cmd.getOptionValue("widevine-pssh"));
cencDrm.setWidevine(widevineDrm);

CencFairPlay cencFairPlay = new CencFairPlay();
cencFairPlay.setIv(cmd.getOptionValue("fairplay-iv"));
cencFairPlay.setUri(cmd.getOptionValue("fairplay-uri"));
cencDrm.setFairPlay(cencFairPlay);
```
