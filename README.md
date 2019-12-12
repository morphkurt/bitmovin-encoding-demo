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



