package com.elroid.wirelens.data.remote;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;

import com.elroid.wirelens.BuildConfig;
import com.elroid.wirelens.domain.GoogleVisionRemoteRepository;
import com.elroid.wirelens.model.CredentialsImage;
import com.elroid.wirelens.model.OcrResponse;
import com.elroid.wirelens.util.FileUtils;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.TextAnnotation;
import com.google.common.io.BaseEncoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import io.reactivex.Single;
import timber.log.Timber;

/**
 * Class: com.elroid.wirelens.data.remote.GoogleVisionServiceClient
 * Project: WireLens
 * Created Date: 22/01/2018 15:30
 *
 * @author <a href="mailto:e@elroid.com">Elliot Long</a>
 *         Copyright (c) 2018 Elroid Ltd. All rights reserved.
 */
public class GoogleVisionServiceClient implements GoogleVisionRemoteRepository
{
	private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
	private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";

	private static final int MAX_BITMAP_WIDTH = 1200;

	private final Context ctx;

	@Inject
	public GoogleVisionServiceClient(Context ctx){
		this.ctx = ctx;
	}


	@Override
	public Single<OcrResponse> getVisionResponse(CredentialsImage image){
		//return Single.just(new GoogleVisionResponse("Your message here"));
		return Single.create(emitter -> {
			try{
				Bitmap bitmap = image.getBitmap();
				bitmap = FileUtils.scaleBitmapToWidth(bitmap, MAX_BITMAP_WIDTH);
				OcrResponse gvr = callGoogleVision(bitmap);
				emitter.onSuccess(gvr);
			}
			catch(Exception e){
				Timber.w(e, "Google vision error");
				emitter.onError(e);
			}
		});
	}

	private OcrResponse callGoogleVision(Bitmap bitmap) throws IOException{

		HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
		JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

		VisionRequestInitializer requestInitializer =
			new VisionRequestInitializer(BuildConfig.API_KEY)
			{
				/**
				 * We override this so we can inject important identifying fields into the HTTP
				 * headers. This enables use of a restricted cloud platform API key.
				 */
				@Override
				protected void initializeVisionRequest(VisionRequest<?> visionRequest)
					throws IOException{
					super.initializeVisionRequest(visionRequest);

					String packageName = ctx.getPackageName();
					visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

					String sig = getSignature(ctx.getPackageManager(), packageName);

					visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
				}
			};

		Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
		builder.setVisionRequestInitializer(requestInitializer);

		Vision vision = builder.build();

		BatchAnnotateImagesRequest batchAnnotateImagesRequest =
			new BatchAnnotateImagesRequest();
		batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>()
		{{
			AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

			// Add the image
			com.google.api.services.vision.v1.model.Image base64EncodedImage
				= new com.google.api.services.vision.v1.model.Image();
			// Convert the bitmap to a JPEG
			// Just in case it's a format that Android understands but Cloud Vision doesn't
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
			byte[] imageBytes = byteArrayOutputStream.toByteArray();

			// Base64 encode the JPEG
			base64EncodedImage.encodeContent(imageBytes);
			annotateImageRequest.setImage(base64EncodedImage);

			// add the features we want
			annotateImageRequest.setFeatures(new ArrayList<Feature>()
			{{
				Feature labelDetection = new Feature();
				labelDetection.setType("TEXT_DETECTION");
				labelDetection.setMaxResults(10);
				add(labelDetection);
			}});

			// Add the list of one thing to the request
			add(annotateImageRequest);
		}});

		Vision.Images.Annotate annotateRequest =
			vision.images().annotate(batchAnnotateImagesRequest);
		// Due to a bug: requests to Vision API containing large images fail when GZipped.
		annotateRequest.setDisableGZipContent(true);
		Timber.d("created Cloud Vision request object, sending request");

		BatchAnnotateImagesResponse response = annotateRequest.execute();
		return convertResponseToString(response);

	}

	private OcrResponse convertResponseToString(BatchAnnotateImagesResponse response){
		/*try{
			Timber.v("Response: " + response.toPrettyString());
		}
		catch(Exception e){
			e.printStackTrace();
		}*/

		AnnotateImageResponse aResponse = response.getResponses().get(0);
		Timber.v("raw response: %s", aResponse);
		TextAnnotation text = aResponse.getFullTextAnnotation();

		String result = text == null ? "" : text.getText();
		Timber.d("returning google image result: '%s'", result);
		return new OcrResponse(result, OcrResponse.Type.GOOGLE_VISION);
	}

	/**
	 * Gets the SHA1 signature, hex encoded for inclusion with Google Cloud Platform API requests
	 *
	 * @param packageName Identifies the APK whose signature should be extracted.
	 * @return a lowercase, hex-encoded
	 */
	private static String getSignature(@NonNull PackageManager pm, @NonNull String packageName){
		try{
			PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
			if(packageInfo == null
				|| packageInfo.signatures == null
				|| packageInfo.signatures.length == 0
				|| packageInfo.signatures[0] == null){
				return null;
			}
			return signatureDigest(packageInfo.signatures[0]);
		}
		catch(PackageManager.NameNotFoundException e){
			Timber.w(e, "Package name error");
			return null;
		}
	}

	private static String signatureDigest(Signature sig){
		byte[] signature = sig.toByteArray();
		try{
			MessageDigest md = MessageDigest.getInstance("SHA1");
			byte[] digest = md.digest(signature);
			return BaseEncoding.base16().lowerCase().encode(digest);
		}
		catch(NoSuchAlgorithmException e){
			Timber.w(e, "Error getting digest");
			return null;
		}
	}

}