package ch.rewop.bildkombinierer;

import java.io.File;

import eu.janmuller.android.simplecropimage.CropImage;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class Erfasser extends Activity {
	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1;
	private static final int REQUEST_CODE_CROP_IMAGE = 0;
	private Uri uriImage;
	private Uri uriFolder;
	private CustomLayout cl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_erfasser);
		
		cl = (CustomLayout) findViewById(R.id.mainlayout);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem menuItem = menu.add("Bild hinzufügen");
		menuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				makeFoto();
				return false;
			}
		});

		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    super.onActivityResult(requestCode, resultCode, data);
	    switch (requestCode) {
	    case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
	        if (resultCode == Activity.RESULT_OK) {
	            Uri selectedImage = uriImage;
	            getContentResolver().notifyChange(selectedImage, null);
	            ContentResolver cr = getContentResolver();
	            Bitmap bitmap;
	            try {
	                 bitmap = android.provider.MediaStore.Images.Media
	                 .getBitmap(cr, selectedImage);
	                Toast.makeText(this, selectedImage.toString(),
	                        Toast.LENGTH_LONG).show();
	            } catch (Exception e) {
	                Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT)
	                        .show();
	                Log.e("Camera", e.toString());
	            }
	            runCropImage();
	        }
	        break;
	    case REQUEST_CODE_CROP_IMAGE:
	    	if (resultCode == Activity.RESULT_OK) {
	    		String path = uriImage.getPath();
	    		Bitmap bitmap = BitmapFactory.decodeFile(path);

	    		// cropped bitmap
	            Toast.makeText(this, path,
	            		Toast.LENGTH_LONG).show();
	            TouchImageView tiv = new TouchImageView(this);
	            
	            tiv.setBitmap(modPic(bitmap));
	            cl.addView(tiv);
	    	}

            break;
	    }
	    
	}
	
	private void makeFoto(){
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		
		File imagesFolder = new File(Environment.getExternalStorageDirectory(), "MyImages");
        imagesFolder.mkdirs(); // <----
        uriFolder = Uri.fromFile(imagesFolder);
        File image = new File(imagesFolder, "image_001.jpg");
        uriImage = Uri.fromFile(image);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uriImage);

	    // start the image capture Intent
	    startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
	}
	
	private void runCropImage() {

	    // create explicit intent
	    Intent intent = new Intent(this, CropImage.class);

	    // tell CropImage activity to look for image to crop 
	    String filePath = uriImage.getPath();
	    intent.putExtra(CropImage.IMAGE_PATH, filePath);

	    // allow CropImage activity to rescale image
	    intent.putExtra(CropImage.SCALE, true);

	    // if the aspect ratio is fixed to ratio 3/2
	    intent.putExtra(CropImage.ASPECT_X, 3);
	    intent.putExtra(CropImage.ASPECT_Y, 2);

	    // start activity CropImage with certain request code and listen
	    // for result
	    startActivityForResult(intent, REQUEST_CODE_CROP_IMAGE);
	} 
	
	private Bitmap modPic(Bitmap pic){
		Bitmap bmOut;
		
		bmOut = doBrightness(pic, 50);
		bmOut = doContrast(bmOut, 100);
		
		return bmOut;
	}
	
	private Bitmap doContrast(Bitmap pic, double value){
		// image size
	    int width = pic.getWidth();
	    int height = pic.getHeight();
	    // create output bitmap

	    // create a mutable empty bitmap
	    Bitmap bmOut = Bitmap.createBitmap(width, height, pic.getConfig());

	    // create a canvas so that we can draw the bmOut Bitmap from source bitmap
	    Canvas c = new Canvas();
	    c.setBitmap(bmOut);

	    // draw bitmap to bmOut from src bitmap so we can modify it
	    c.drawBitmap(pic, 0, 0, new Paint(Color.BLACK));


	    // color information
	    int A, R, G, B;
	    int pixel;
	    // get contrast value
	    double contrast = Math.pow((100 + value) / 100, 2);

	    // scan through all pixels
	    for(int x = 0; x < width; ++x) {
	        for(int y = 0; y < height; ++y) {
	            // get pixel color
	            pixel = pic.getPixel(x, y);
	            A = Color.alpha(pixel);
	            // apply filter contrast for every channel R, G, B
	            R = Color.red(pixel);
	            R = (int)(((((R / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
	            if(R < 0) { R = 0; }
	            else if(R > 255) { R = 255; }

	            G = Color.green(pixel);
	            G = (int)(((((G / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
	            if(G < 0) { G = 0; }
	            else if(G > 255) { G = 255; }

	            B = Color.blue(pixel);
	            B = (int)(((((B / 255.0) - 0.5) * contrast) + 0.5) * 255.0);
	            if(B < 0) { B = 0; }
	            else if(B > 255) { B = 255; }

	            // set new pixel color to output bitmap
	            bmOut.setPixel(x, y, Color.argb(A, R, G, B));
	        }
	    }
	    return bmOut;
	}
	
	private Bitmap doBrightness(Bitmap src, int value) {
	    // image size
	    int width = src.getWidth();
	    int height = src.getHeight();
	    // create output bitmap
	    Bitmap bmOut = Bitmap.createBitmap(width, height, src.getConfig());
	    // color information
	    int A, R, G, B;
	    int pixel;
	 
	    // scan through all pixels
	    for(int x = 0; x < width; ++x) {
	        for(int y = 0; y < height; ++y) {
	            // get pixel color
	            pixel = src.getPixel(x, y);
	            A = Color.alpha(pixel);
	            R = Color.red(pixel);
	            G = Color.green(pixel);
	            B = Color.blue(pixel);
	 
	            // increase/decrease each channel
	            R += value;
	            if(R > 255) { R = 255; }
	            else if(R < 0) { R = 0; }
	 
	            G += value;
	            if(G > 255) { G = 255; }
	            else if(G < 0) { G = 0; }
	 
	            B += value;
	            if(B > 255) { B = 255; }
	            else if(B < 0) { B = 0; }
	 
	            // apply new pixel color to output bitmap
	            bmOut.setPixel(x, y, Color.argb(A, R, G, B));
	        }
	    }
	 
	    // return final image
	    return bmOut;
	}

}
