package com.aj.shared.plugin

@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class HostRequired(val reason: String, val wireVia: String = "EazyCmp.install { ... }")
