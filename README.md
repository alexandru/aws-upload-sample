# aws-upload-sample

Demonstrates usage of AWS SDK's 
[TransferManager](docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/transfer/TransferManager.html)
for handling S3 uploads.

Notes:

- the thread-pool given to [S3Uploader](src/main/scala/sample/S3Uploader.scala) must be 
  configured for blocking I/O because the `TransferManager` blocks the used threads on uploading
  chunks, preventing progress in processing the thread-pool's queue
   
- getting the result in case of errors is problematic, 
  as I found no way to do it without blocking for it and 
  `request.waitForUploadResult` does happen to block the thread, 
  even though you may receive a transfer complete event, but given that
  `TransferManager` is blocking those threads anyway, I see no harm in
  that
  
## Configuration

Configuration lives in [application.conf](src/main/resources/application.conf) and
must be configured with access keys:

```javascript
aws.s3 {
  accessKey = "xxxxx"
  secretAccessKey = "xxxxxxxxxxxxxx"
  bucketName = "someBucket"
  region = "eu-west-1"
}
```
