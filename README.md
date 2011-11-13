# Foursquare Finagle Http Library

Finagle is a wonderful protocol agnostic communication library.
Building an http client using finagle is super simple.

However, building a request and parsing the response using the netty
library in scala is a chore compared building the client.
FHttp is a scala-idiomtic request building interface similar to scalaj-http for finagle http clients.

Like scalaj-http, it supports multipart data and oauth1.

