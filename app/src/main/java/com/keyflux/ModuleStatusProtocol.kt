package com.keyflux

/** Intent contract used to verify that this exact module build is running in Gboard. */
internal object ModuleStatusProtocol {
    const val REQUEST_ACTION = "com.keyflux.action.REQUEST_MODULE_STATUS"
    const val RESPONSE_ACTION = "com.keyflux.action.MODULE_STATUS"
    const val EXTRA_NONCE = "nonce"
    const val EXTRA_MODULE_VERSION_NAME = "module_version_name"
    const val EXTRA_MODULE_VERSION_CODE = "module_version_code"
    const val EXTRA_XPOSED_API = "xposed_api"
    const val EXTRA_FAILED_HOOK_COUNT = "failed_hook_count"
    const val EXTRA_PROCESS_TOKEN = "process_token"
}
