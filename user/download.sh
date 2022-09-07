source ../.init

if [ -z "$1" ]; then
  echo "Have to specify object as \$1. E.g.: ./download.sh prefix1/data1.csv"
  exit
fi

aws s3api get-object --bucket $DATA_BUCKET_AP_ARN --key "$1" /dev/stdout