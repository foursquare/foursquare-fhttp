// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.fhttp

import com.twitter.conversions.time._
import com.twitter.finagle.builder.{ClientBuilder, ServerBuilder}
import com.twitter.finagle.http.Http
import com.twitter.finagle.{Service, TimeoutException}
import com.twitter.util.Future
import org.jboss.netty.channel.DefaultChannelConfig
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.junit.{After, Before, Test}
import org.specs._
import java.net.{InetSocketAddress, SocketAddress}
import scala.collection.JavaConversions._

object FHttpRequestValidators extends SpecsMatchers  {
  def matchesHeader(key: String, value: String): FHttpRequest.HttpOption = r => {
    r.getHeaders(key) == null must_== false
    r.getHeaders(key).mkString("|") must_== value
  }

  def matchesContent(content: String, length: Int): FHttpRequest.HttpOption = r => {
    matchesHeader(HttpHeaders.Names.CONTENT_LENGTH, length.toString)(r)
    r.getContent.toString(FHttpRequest.UTF_8) must_== content
  }

  def containsContent(content: String): FHttpRequest.HttpOption = r => {
    r.getContent.toString(FHttpRequest.UTF_8) must include(content)
  }

}

class FHttpTestHelper (serverPort: Int) extends SpecsMatchers {
  var serverWaitMillis: Int = 0
  var responseTransforms: List[FHttpRequest.HttpOption] = Nil
  var requestValidators: List[FHttpRequest.HttpOption] = Nil
  var responseStatus = OK
  
  def reset() = {
    requestValidators = Nil
    responseTransforms = Nil
    responseStatus = OK
  }

  def serverResponse: HttpResponse = {
    var res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, responseStatus)
    responseTransforms.reverse.foreach(_(res))
    res
  }

  val service: Service[HttpRequest, HttpResponse] = new Service[HttpRequest, HttpResponse] { 
    def apply(request: HttpRequest) = {
      try {
        requestValidators.foreach(_(request))
        responseTransforms ::= {
          (r: HttpMessage) => {
            HttpHeaders.setContentLength(r, 0)
          }
        }

      } catch {
        case exc: specification.FailureExceptionWithResult[_] => 
          responseTransforms ::= {
            (r: HttpMessage) => {
              val data = exc.toString.getBytes(FHttpRequest.UTF_8)
              r.setContent(new DefaultChannelConfig().getBufferFactory.getBuffer(data, 0, data.length))
              HttpHeaders.setContentLength(r, data.length)
            }
          }

        case e => throw e
      }
      Thread.sleep(serverWaitMillis)
      Future(serverResponse)
    }
  }

  val address: SocketAddress = new InetSocketAddress("localhost", serverPort)                                  

  val server = ServerBuilder()                            
    .codec(Http())
    .bindTo(address)
    .name("HttpServer")
    .maxConcurrentRequests(20)
    .build(service)

   
}

object PortHelper {
  var port = 8101
}

class FHttpClientTest extends SpecsMatchers {
  var helper: FHttpTestHelper = null
  var client: FHttpClient = null
  @Before
  def setupHelper {
    helper = new FHttpTestHelper(PortHelper.port)
    client = new FHttpClient("test-client","localhost:" + PortHelper.port)
    PortHelper.port += 1
  }

  @After
  def teardownHelper {
    client.service.release()
    helper.server.close()
  }

  @Test
  def testRequestAddParams {
    val expected1 = "/test"
    val expected2 = expected1 + "?this=is%20silly&no=you%2Bare"
    val expected3 = expected2 + "&no=this_is"
    val req1 = client("/test")
    req1.uri must_== expected1

    val req2 = req1.params("this"->"is silly","no"->"you+are")
    req2.uri must_== expected2
    
    // params get appended if called again
    val req3 = req2.params("no"->"this_is")
    req3.uri must_== expected3

    req3.params().uri must_== expected3
  }

  @Test
  def testRequestAddHeaders {
    helper.requestValidators = FHttpRequestValidators.matchesHeader("name", "johng") ::
      FHttpRequestValidators.matchesHeader("Host", client.hostPort) :: Nil
    val req = FHttpRequest(client, "/test").headers("name"->"johng")
    val resErr = req.timeout(5000).get_!()
    resErr isEmpty
    
    // must match both
    helper.requestValidators ::= FHttpRequestValidators.matchesHeader("city", "ny")
    val req2 = req.headers("city"->"ny")
    val resErr2 = req2.timeout(5000).get_!()
    resErr2 isEmpty

    // adding a header with the same key appends, not replaces
    helper.requestValidators = FHttpRequestValidators.matchesHeader("city", "ny|sf") ::
                              helper.requestValidators.tail
    val req3 = req2.headers("city"->"sf")
    val res3 = req3.timeout(5000).get_!()
    res3 must_== ""

    // adding a header with the same key appends, not replaces
    helper.requestValidators = FHttpRequestValidators.matchesHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==") ::
                              helper.requestValidators.tail
    val req4 = req3.auth("Aladdin", "open sesame")
    val res4 = req4.timeout(5000).get_!()
    res4 must_== ""
  }

  @Test
  def testSetContent {
    helper.requestValidators = FHttpRequestValidators.matchesContent("hi", 2) :: Nil
    val req = FHttpRequest(client, "/test").timeout(5000).post_!("hi")
    req must_== ""

    // Empty
    helper.requestValidators = FHttpRequestValidators.matchesContent("", 0) :: Nil
    val reqEmpty = FHttpRequest(client, "/test").timeout(5000).post_!("")
    reqEmpty must_== ""

  }

  @Test
  def testSetMultipart {
    val xml = """
      <?xml version="1.0"?>
      <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
        <soap:Header>
        </soap:Header>
        <soap:Body>
          <m:GetStockPrice xmlns:m="http://www.example.org/stock">
            <m:StockName>IBM</m:StockName>
          </m:GetStockPrice>
        </soap:Body>
      </soap:Envelope>
      """
    val xmlbytes = xml.getBytes(FHttpRequest.UTF_8)
    val part1 = MultiPart("soap", "soap.xml", "application/soap+xml", xmlbytes)
    val json = """ { "some": "json" }"""
    val jsonBytes = json.getBytes(FHttpRequest.UTF_8)
    val part2 = MultiPart("json", "some.json", "application/json", jsonBytes)
    
    helper.requestValidators =  
      FHttpRequestValidators.containsContent("Content-Disposition: form-data; name=\"hi\"") ::
      FHttpRequestValidators.containsContent("you") ::
      FHttpRequestValidators.containsContent(xml) ::
      FHttpRequestValidators.containsContent(json) ::
      FHttpRequestValidators.matchesHeader(HttpHeaders.Names.CONTENT_LENGTH, "908") :: Nil

    val reqEmpty = FHttpRequest(client, "/test").params("hi"->"you")
      .timeout(5000)
      .post_!(part1 :: part2 :: Nil, FHttpRequest.asString)
    reqEmpty must_== ""
  }

  @Test 
  def testExceptionOnNonOKCode {
    helper.responseStatus = NOT_FOUND
    try {
      val reqNotFound = FHttpRequest(client, "/notfound").timeout(5000).get_!() 
      throw new Exception("this should not have succeeded")
    } catch {
      case HttpStatusException(code, reason, response) if (code == NOT_FOUND.getCode)  =>
      case _ => throw new Exception("wrong code")
    }

  }

  @Test
  def testExceptionOnTimeout {
    helper.serverWaitMillis = 10
    try {
      val reqTimedOut = FHttpRequest(client, "/timeout").timeout(1).get_!()
    } catch {
      case e: TimeoutException =>
      case e => throw new RuntimeException("should have thrown a TimeoutException")
    }
  }

  @Test
  def testFutureTimeout {
    helper.serverWaitMillis = 100
    var gotResult = false
    val f = FHttpRequest(client, "/future0").timeout(1).getFuture() onSuccess {
      r => gotResult = true
    } onFailure {
      e => gotResult = true
    }
    while (!gotResult) {
      Thread.sleep(10)
    }
    try {
      val r = f.get
      throw new Exception("should have timed out but got " + r)
    } catch {
      case _ => Unit
    }
  }


  @Test
  def testFutureResult {
    var r1 = "not set"
    var r2 = -1
    FHttpRequest(client, "/future1").timeout(5000).getFuture() onSuccess {
      r => r1 = r
    } onFailure {
      e => throw new Exception(e)
    }

    
    //asBytes
    FHttpRequest(client, "/future2").timeout(5000).getFuture(FHttpRequest.asBytes) onSuccess {
      r => r2 = r.length
    } onFailure {
      e => throw new Exception(e)
    }
    while( r1 == "not set" || r2 < 0) {
      Thread.sleep(10)
    }

    r1 must_== ""
    r2 must_== 0
  }

  @Test
  def testLBHostHeaderUsesFirstHost {
    val port = client.firstHostPort.split(":",2)(1)
    val client2 = new FHttpClient("test-client-2", "localhost:" + port + ",127.0.0.1:" + port)
    helper.requestValidators = List(FHttpRequestValidators.matchesHeader("Host", "localhost:" + port))
    client2("/test").get_!() must_== ""
    client2.release()

    val client3 = new FHttpClient("test-client-2", "127.0.0.1:" + port + ",localhost:" + port)
    helper.requestValidators = List(FHttpRequestValidators.matchesHeader("Host", "127.0.0.1:" + port))
    client3("/test").get_!() must_== ""
    client3.release()
  }


  @Test
  def testOauthFlowGetPost {
    def testFlow(usePost: Boolean) {
      import com.foursquare.fhttp.FHttpRequest.asOAuth1Token
      val clientOA =
        new FHttpClient(
          "oauth",
          "term.ie:80",
          (ClientBuilder()
            .codec(Http())
            .hostConnectionLimit(1))
            .tcpConnectTimeout(1.seconds))
      val consumer = Token("key", "secret")

      // Get the request token
      val token = {
        val tkReq = clientOA("/oauth/example/request_token").oauth(consumer)
        if(usePost) tkReq.post_!("", asOAuth1Token) else tkReq.get_!(asOAuth1Token)
      }

      // Get the access token
      val accessToken = {
        val atReq = clientOA("/oauth/example/access_token").oauth(consumer, token)
        if(usePost) atReq.post_!("", asOAuth1Token) else atReq.get_!(asOAuth1Token)
      }

      // Try some queries
      val testParamsRes = {
        val testReq = clientOA("/oauth/example/echo_api").params("k1"->"v1", "k2"->"v2")
          .oauth(consumer, accessToken)
        if(usePost) testReq.post_!() else testReq.get_!()
      }
      testParamsRes must_== "k1=v1&k2=v2"
    }

    testFlow(false)
    testFlow(true)
  }

}
