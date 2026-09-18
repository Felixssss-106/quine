package com.quine.core.gateway

import com.quine.core.common.QuineError

/** 网关内统一的失败载体，携带人话错误。 */
class LlmException(val error: QuineError) : Exception(error.message, error.cause)
