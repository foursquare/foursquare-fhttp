# Foursquare Finagle Http Library #

[Finagle](https://github.com/twitter/finagle) is a wonderful protocol agnostic communication library.
Building an http client using finagle is super simple.

However, building an http request and parsing the response using the netty
library in scala is a chore compared building the client.
FHttp is a scala-idiomatic request building interface similar to 
[scalaj-http](https://github.com/scalaj/scalaj-http) for finagle http clients.

Like [scalaj-http](https://github.com/scalaj/scalaj-http), it supports multipart data and oauth1.

You will probably want to override FHttpClient.service to add your own logging and tracing filters.

## Simple Example ##

## OAuth Example ##
    import com.foursquare.fhttp._
    import com.foursquare.fttp.FRequest._
    import com.twitter.finagle.http.Http
    import com.twitter.finagle.ClientBuilder

    // Create the singleton client object from a partially complete client spec
    val client = new FHttpClient("oauth", "term.ie:80", ClientBuilder().codec(Http()).hostConnectionLimit(1))
    val consumer = val consumer = Token("key", "secret")
    
    // Get the request token
    val token = client("/oauth/example/request_token").oauth(consumer).get_!(asOAuth1Token)

    // Get the access token
    val accessToken = client("/oauth/example/access_token").oauth(consumer, token).get_!(asOAuth1Token)

    // Try some queries
    client("/oauth/example/echo_api").params("k1"->"v1", "k2"->"v2").oauth(consumer, accessToken).get_!()
    // res9: String = k1=v1&k2=v2


