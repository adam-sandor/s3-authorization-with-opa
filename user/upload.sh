source ../.init

aws s3 cp data1.csv s3://$DATA_BUCKET/data1.csv
aws s3 cp data1.csv s3://$DATA_BUCKET/prefix1/data1.csv
aws s3 cp data1.csv s3://$DATA_BUCKET/prefix2/data1.csv