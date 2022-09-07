source ../.init
aws s3api get-object --bucket $DATA_BUCKET_AP_ARN --key "$1" /dev/stdout