# paye-registration

[![Build Status](https://travis-ci.org/hmrc/paye-registration.svg)](https://travis-ci.org/hmrc/paye-registration) [ ![Download](https://api.bintray.com/packages/hmrc/releases/paye-registration/images/download.svg) ](https://bintray.com/hmrc/releases/paye-registration/_latestVersion)

Microservice supporting the paye registration aspects of the Streamlined Company Registration Legislation.

## Running the Application

In order to run the microservice, you must have SBT installed. You should then be able to start the application using: 

```sbt "run {PORTNUM}"```

To run the tests for the application, you can run: ```sbt test it:test``` 

or ```sbt coverage test it:test coverageReport```

## API

| Path                         | Supported Methods |                                    Description                                          |
| -----------------------------| ------------------| --------------------------------------------------------------------------------------- |
|```/:registrationId/status``` |        GET        | Retrieve the submission status for the user associated with the supplied registrationId |

### GET /:registrationID/status

    Responds with:


| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 403           | Forbidden     |
| 404           | Not found     |


#### Example of usage

GET /:registrationID/status

with header:

Authorization Bearer fNAao9C4kTby8cqa6g75emw1DZIyA5B72nr9oKHHetE=

**Request body**

No request body required.

**Response body**

A ```200``` success response:

```json
{
    "status": "submitted",
    "last_updated": "2222-12-12",
    "acknowledgement_reference": "AAAA-1234567890",
}
```

The error scenarios will return an error document, for example :
```
{
    "statusCode": 404,
    "message":"Could not find an existing PAYE Registration document with that Registration ID"
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")