// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package com.foursquare.fhttp

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.joauth._
import com.twitter.util.Throw
import org.jboss.netty.handler.codec.http._
import scala.collection.JavaConversions._

case class Token(key: String, secret: String)

class OAuth1Filter (scheme: String,
                      host: String,
                      port:Int,
                      consumer: Token,
                      token: Option[Token],
                      verifier: Option[String]) extends SimpleFilter[HttpRequest, HttpResponse] {

  def apply(request: HttpRequest, service: Service[HttpRequest, HttpResponse]) = {

    val time = System.currentTimeMillis/1000

    object authParams extends OAuth1Params( token.map(_.key).getOrElse(null),
                                            consumer.key,
                                            System.currentTimeMillis.toString, //nonce
                                            time,
                                            time.toString,
                                            null,
                                            OAuthParams.HMAC_SHA1,
                                            OAuthParams.ONE_DOT_OH) {

      override def toList(includeSig: Boolean): List[(String, String)] = super.toList(false).filter(_._2 != null)
    }

    val uri = new java.net.URI(request.getUri)
    val stdParams: List[(String,String)] = {
      val queryString = {
        if(request.getMethod == HttpMethod.GET) {
          uri.getQuery
        } else {
          request.getContent.toString(FHttpRequest.UTF_8)
        }
      }

      Option(queryString).toList. flatMap {
        _.split("&").flatMap(_.split("=") match {
          case Array(k,v) => Some(k, v)
          case _ => None
        })
      }
    }

    val verParams = verifier.map(v => List("oauth_verifier" -> v)).getOrElse(Nil)
    val normStr = StandardNormalizer( scheme,
                                      host,
                                      port,
                                      request.getMethod.getName,
                                      uri.getPath,
                                      verParams ::: stdParams,
                                      authParams)
    val sig = Signer()(normStr, token.map(_.secret).getOrElse(""), consumer.secret)
    val allAuthParams = (verParams ::: (OAuthParams.OAUTH_SIGNATURE -> sig) ::authParams.toList(false))

    // finally, add the header
    request.addHeader("Authorization", "OAuth " +
      allAuthParams.map(p => p._1 + "=\"" + percentEncode(p._2) + "\"").mkString(","))

    service(request)
  }

  def percentEncode(s: String) = 
    java.net.URLEncoder.encode(s, "utf-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
}

