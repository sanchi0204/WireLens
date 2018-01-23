package com.elroid.wirelens.uitest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.test.InstrumentationRegistry.getTargetContext
import com.elroid.wirelens.data.local.SimpleTextParser
import com.elroid.wirelens.data.remote.GoogleVisionServiceClient
import com.elroid.wirelens.model.GoogleVisionResponse
import com.elroid.wirelens.model.TextParserResponse
import io.reactivex.observers.TestObserver
import org.junit.Assert.assertEquals
import org.junit.Test
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


/**
 * Class: com.elroid.wirelens.uitest.GoogleVisionServiceTest
 * Project: WireLens
 * Created Date: 23/01/2018 12:49
 *
 * @author <a href="mailto:e@elroid.com">Elliot Long</a>
 *         Copyright (c) 2018 Elroid Ltd. All rights reserved.
 */
class GoogleVisionServiceTest {

	private val googlVisionRemoteService = GoogleVisionServiceClient(getTargetContext())

	@Test
	fun getVisionResponse_given65twentyImage_returnsCorrectResponse() {
		val bmp = getBitmapFromURL("http://elroid.com/wirelens/guest.jpg");

		val obs = googlVisionRemoteService.getVisionResponse(bmp)
		val testObserver = TestObserver<GoogleVisionResponse>()
		obs.subscribe(testObserver)
		testObserver.assertNoErrors()
		assertEquals(testObserver.valueCount(), 1)
		val gvr = testObserver.values()[0]
		val expected = TextParserResponse("65twenty_guest", "guest7ad")
		val parser = SimpleTextParser()
		val actual = parser.parseResponse(gvr).blockingFirst()
		Timber.i("Comparing %s with %s", expected, actual)
		assertEquals(expected, actual)
	}
	/*@Test
	fun getVisionResponse_givenDroidconImage_returnsCorrectResponse() {
		val bmp = getBitmapFromURL("http://elroid.com/wirelens/droidcon2.jpg");

		val obs = googlVisionRemoteService.getVisionResponse(bmp)
		val testObserver = TestObserver<GoogleVisionResponse>()
		obs.subscribe(testObserver)
		testObserver.assertNoErrors()
		assertEquals(testObserver.valueCount(), 1)
		val gvr = testObserver.values()[0]
		val expected = TextParserResponse("droidconuk", "NOugatyNiceness")
		val parser = SimpleTextParser()
		assertEquals(expected, parser.parseResponse(gvr).blockingFirst())
	}*/

	/*@Test
	fun getVisionResponse_givenDroidconImage_returnsCorrectResponse() {
		val bmp = getBitmapFromURL("http://elroid.com/wirelens/droidcon.jpg");

		val obs = googlVisionRemoteService.getVisionResponse(bmp)
		val testObserver = TestObserver<GoogleVisionResponse>()
		obs.subscribe(testObserver)
		testObserver.assertNoErrors()
		assertEquals(testObserver.valueCount(), 1)
		val gvr = testObserver.values()[0]
		val expected = TextParserResponse("droidconuk", "WhatTheL50")
		val parser = SimpleTextParser()
		assertEquals(expected, parser.parseResponse(gvr).blockingFirst())
	}*/

	/*@Test
	fun getVisionResponse_givenImage_returnsResponse(){
		val bmp = getBitmapFromURL("http://elroid.com/wirelens/droidcon.jpg");

		val obs = googlVisionRemoteService.getVisionResponse(bmp)
		val testObserver = TestObserver<GoogleVisionResponse>()
		obs.subscribe(testObserver)
		testObserver.assertNoErrors()
		assertEquals(testObserver.valueCount(), 1)
		val gvr = testObserver.values()[0]
		assertNotNull(gvr.text)
		Timber.i("Response: %s", gvr.text)
	}*/


	fun getBitmapFromURL(src: String): Bitmap? {
		try {
			val url = URL(src)
			val connection = url.openConnection() as HttpURLConnection
			connection.doInput = true
			connection.connect()
			val input = connection.inputStream
			return BitmapFactory.decodeStream(input)
		} catch (e: IOException) {
			e.printStackTrace()
			return null
		}
	}
}