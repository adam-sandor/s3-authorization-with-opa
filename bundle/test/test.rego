package test

import data.rules as rules

test_us_user_accessing_org1_denied {
  not rules.allow with input as {
    "userIdentity": {
      "arn": "arn:aws:iam::728162064813:user/adam-aws-cli-user",
    },
    "user_request": {
      "url": "https://s3-authz-demo-olap-728162064813.s3-object-lambda.us-east-1.amazonaws.com/prefix2/data1.csv"
    }
  }
  with data.userdata as {
    "arn:aws:iam::728162064813:user_adam-aws-cli-user": {
      "region": "US",
      "security_clearance": 2
    }
  }
}