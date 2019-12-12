# Bitmovin Java Encoding Example 

## Non DRM Solution

### Build the Project

Clone the repo and build the project

```bash

mvn install
mvn package

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

