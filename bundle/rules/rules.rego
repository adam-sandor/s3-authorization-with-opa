package rules

import data.userdata as users

default allow = false

allow {
  count(deny) == 0
}

deny["prefix1 data can only be accessed from the US"] {
  user.region != "US"
  object_path[1] == "prefix1"
}

deny["prefix2 data can only be accessed from outside the US"] {
  user.region == "US"
  object_path[1] == "prefix2"
}

deny["Missing prefix"] {
  count(object_path) == 2
}

deny["Unknown Object"] {
  not object_path
}

data_permissions["public"]

data_permissions["sensitive"] {
  user.security_clearance > 2
}

user := users[replace(input.userIdentity.arn, "/", "_")]

object_path := regex.split("/", regex.split("://", input.user_request.url)[1])
