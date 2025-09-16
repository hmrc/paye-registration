sbt it/test# paye-registration

Microservice supporting the paye registration aspects of the Streamlined Company Registration Legislation.

## Running the Application

In order to run the microservice, you must have SBT installed. You should then be able to start the application using: 

```bash
./run.sh
```

## Running locally
Use service manager to run all services required by PAYE Registration:

```bash
sm2 --start PAYE_REG_ALL
```

To run the tests for the application, you can run: ```sbt test it/test``` 


or ```sbt coverage test it/test coverageReport```

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
    "lastUpdate": "2017-05-09T07:58:35Z",
    "ackRef": "AAAA-1234567890"
}
```

A ```200``` success response with a restartURL (use GET verb) when status is ```rejected```:

```json
{
   "status": "rejected",
   "lastUpdate": "2017-05-09T07:58:35Z",
   "ackRef": "AAAA-1234567890",
   "restartURL": "http://server:port/uriToRestart"
}
```

A ```200``` success response with a cancelURL (use DELETE verb) when status is ```draft``` or ```invalid```:

```json
{
   "status": "draft",
   "lastUpdate": "2017-05-09T07:58:35Z",
   "ackRef": "AAAA-1234567890",
   "cancelURL": "http://server:port/uriToCancel"
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