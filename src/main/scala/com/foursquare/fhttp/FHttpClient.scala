// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.fhttp

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.builder.ClientConfig.Yes
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._

class FHttpClient ( val name: String,
                          val hostPort: String, // host:port
                          builder: ClientBuilder[HttpRequest, HttpResponse, Nothing, Yes, Yes],
                          val logFailureEvery: Int = 0) {

  object throwHttpErrorsFilter extends SimpleFilter[HttpRequest, HttpResponse] {
    def apply(request: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {
      // flatMap asynchronously responds to requests and can "map" them to both
      // success and failure values:
      service(request) flatMap { response =>
        response.getStatus.getCode match {
          case x if (x >= 200 && x < 300) => 
            Future.value(response)
          case code => 
            Future.exception(HttpStatusException(code, response.getStatus.getReasonPhrase).addName(name))
        }
      }
    }
  }

  // hackazor!
  def scheme = if(builder.toString.contains("tls=")) "https" else "http"

  def builtClient = builder.name(name).hosts(hostPort).build() 

  val baseService = throwHttpErrorsFilter andThen builtClient

  def service: Service[HttpRequest, HttpResponse] = baseService

  def uri(path: String): FHttpRequest = {
    FHttpRequest(this, path)
  }

  def apply(path: String): FHttpRequest = {
    uri(path)
  }

  override def toString(): String =  {
    return "com.foursquare.fhttp.FHttpClient(" + name + "," + scheme + "://" + hostPort + "," + builder + ")"
  }
}


