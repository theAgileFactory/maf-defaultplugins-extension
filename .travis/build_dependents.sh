#!/bin/bash

body='{
"request": {
  "branch":"master"
}}'

echo "Building the dependent project : bizdock-packaging"
curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Travis-API-Version: 3" \
  -H "Authorization: token $TTOKEN" \
  -d "$body" \
  https://api.travis-ci.org/repo/theAgileFactory%2Fbizdock-packaging/requests
