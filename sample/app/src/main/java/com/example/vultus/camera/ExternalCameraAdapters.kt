package com.example.vultus.camera

import io.haii.vultus.VultusExternalCameraAdapter

/**
 * 샘플이 SDK 에 붙이는 외장(USB) 카메라 어댑터 목록. 비어 있으면 외장 카메라에 UVC 요청을 보내지 않는다.
 *
 * 기기에 단 카메라에 맞는 어댑터를 만들어 여기에 넣는다 — 만드는 법은 가이드 §8.2(`MyWebcamAdapter`).
 */
val externalCameraAdapters: List<VultusExternalCameraAdapter> = emptyList()
