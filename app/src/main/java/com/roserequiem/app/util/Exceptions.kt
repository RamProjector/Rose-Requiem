package com.roserequiem.app.util

class NoTrackFoundException : Exception()
class InternalErrorException(msg: String) : Exception(msg)
class EmptyQueryException : Exception()