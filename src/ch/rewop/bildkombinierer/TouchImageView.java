package ch.rewop.bildkombinierer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

/**
 * Eine View um ein Bitmap darzustellen welches mittels Touch verschoben,
 * skaliert und gezoomt werden kann.
 * 
 * Diese Klasse basiert auf folgendem Blog:
 * http://android-developers.blogspot.ch/2010/06/making-sense-of-multitouch.html
 */
public class TouchImageView extends View {

	/**
	 * Die Position an der sich das Bild im Moment befindet
	 */
	private float posX;
	private float posY;

	/**
	 * Der zuletzt berührte Punkt. Wird gebraucht um bei einer Verschiebung zu
	 * ermitteln in welche Richtung und wie weit verschoben wurde.
	 */
	private float lastTouchX;
	private float lastTouchY;

	/**
	 * Für das Skalieren können wir auf eine bereits in Android vorhandene
	 * Komponente zurückgreifen.
	 */
	private final ScaleGestureDetector scaleDetector;
	private float scaleFactor = 1f;

	/**
	 * Auch für das berechnen der Rotation merken wir uns den letzten Wert um
	 * die Differenz berechnen zu können.
	 */
	private float lastRotation = 0f;

	/**
	 * Zusätzlich merken wir uns den ganzen Winkel um den das Bild gedreht ist.
	 */
	private float totalRotation = 0f;

	/**
	 * Initiale Touch Events (also zu Beginn einer Berührung mit einem Finger)
	 * die nicht auf unserem Bild sind wollen wir ignorieren, deshalb müssen wir
	 * uns unsere eigene Position auf dem Screen merken.
	 */
	private final RectF boundingBox = new RectF();

	/**
	 * Das Bild das wird in der View rendern.
	 */
	private Bitmap bitmap;
	private RectF bitmapSize;

	public TouchImageView(Context context) {
		this(context, null, 0);
	}

	public TouchImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TouchImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		/*
		 * Android enthält bereits eine Komponente um sogenannte Pinch-to-Zoom
		 * Events zu erkennen. Wir brauchen diese bloss noch zu registrieren.
		 */

		scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector detector) {
				scaleFactor *= detector.getScaleFactor();

				/*
				 * Wir übernehmen den Skalierungsfaktor nicht 1:1 sondern
				 * begrenzen ihn auf 0.1 - 5 x, um nicht zu kleine oder zu
				 * grosse Bilder zu haben.
				 */
				scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));

				invalidate();
				return true;
			}
		});
	}

	/**
	 * Ein Setter für das anzuzeigende Bild. Diese Methode muss aufgerufen
	 * nachdem die View erstellt wurde.
	 */
	public void setBitmap(Bitmap bitmap) {

		DisplayMetrics metrics = new DisplayMetrics();
		((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);

		int displayWidth = metrics.widthPixels;
		int displayHeight = metrics.heightPixels;
		float largerBitmapDisplayRatio = Math.max(((float) bitmap.getWidth()) / displayWidth,
				((float) bitmap.getHeight()) / displayHeight);

		/*
		 * Wir skalieren das Bitmap auf eine auf das Display angepasste Grösse
		 * herunter, so dass wir nicht mit einem zu grossen oder zu kleinen Bild
		 * starten. Ausserdem ist die maximale Grösse einer Textur beschränkt,
		 * wir können also nicht ein 10 Megapixel Bild als Textur verwenden.
		 */

		float scaleFactor = 0.8f / largerBitmapDisplayRatio;

		Bitmap smaller = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * scaleFactor),
				(int) (bitmap.getHeight() * scaleFactor), /* filter = */true);

		this.bitmap = makeTransparent(smaller);

		/* Das neue Bild soll in der Mitte der View gezeichnet erscheinen. */
		posX = displayWidth / 2 - this.bitmap.getWidth() / 2;
		posY = displayHeight / 2 - this.bitmap.getHeight() / 2;

		bitmapSize = new RectF(0, 0, this.bitmap.getWidth(), this.bitmap.getHeight());
	}

	/**
	 * Wandelt das von der Kamera aufgenommene Bild in ein halbtransparents
	 * Bitmap um, wobei hellere Stellen transparent werden und dunklere Stellen
	 * fast nicht.
	 */
	public static Bitmap makeTransparent(Bitmap bitmap) {
		if (bitmap != null) {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			int[] data = new int[width * height];
			bitmap.getPixels(data, 0, width, 0, 0, width, height);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int index = y * width + x;

					/*
					 * Wir extrahieren jeden einzelnen Pixel und berechnen dafür
					 * einen für das menschliche Auge passenden Grauwert
					 * (deshalb die unterschiedlichen Gewichtungen der einzelnen
					 * Farben - Blau zum Beispiel wird vom Auge viel schwächer
					 * wahrgenommmen als Grün)
					 */
					int red = Color.red(data[index]);
					int green = Color.green(data[index]);
					int blue = Color.blue(data[index]);
					int alpha = 255 - (int) (0.299 * red + 0.587 * green + 0.114 * blue);

					/*
					 * Wir legen die einzelnen Farben und den Alpha
					 * (Transparenz) Wert im Bitmap.Config.ARGB_8888 format ab.
					 */
					data[index] = (alpha << 24) | (red << 16) | (green << 8) | blue;
				}
			}
			return Bitmap.createBitmap(data, width, height, Bitmap.Config.ARGB_8888);
		}
		return null;
	}

	/**
	 * Jeder Finger auf dem Screen bekommt eine eigene ID zugewiesen damit
	 * zwischen mehreren Fingern unterschieden werden kann. Wir merken uns
	 * deshalb den aktuellen Finger in einer Instanzvariablen. Initialisiert
	 * wird sie mit einer ungültigen ID um anzuzeigen, dass im Moment kein
	 * Finger aktiv ist.
	 */
	private int activePointerId = INVALID_POINTER_ID;

	private static final int INVALID_POINTER_ID = -1;

	/**
	 * Diese Methode wird von Android aufgerufen damit wir auf den Uebergebenen
	 * Canvas (Leinwand) unser Bitmap zeichnen können.
	 */
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		/*
		 * Wir speichern den aktuellen Zustand der Leinwand um ihn am Ende
		 * wiederherstellen zu können. Beim Wiederherstellen mit restore werden
		 * alle Matrixtransformationen auf dem Canvas wieder rückgängig gemacht.
		 */
		canvas.save();

		canvas.translate(posX, posY);

		int centerX = bitmap.getWidth() / 2;
		int centerY = bitmap.getHeight() / 2;

		/*
		 * Da wir die alte Matrix wiederverwenden müssen wir sie zuerst wieder
		 * zurücksetzen.
		 */
		transform.reset();

		/* Wir skalieren und rotieren das Bild um das Zentrum. */

		transform.setScale(scaleFactor, scaleFactor, centerX, centerY);
		transform.postRotate(totalRotation, centerX, centerY);

		canvas.drawBitmap(bitmap, transform, null);
		canvas.restore();

		/*
		 * Dieselbe Transformation wenden wir nun auch auf unsere Bounding Box
		 * an.
		 */
		transform.mapRect(boundingBox, bitmapSize);
		boundingBox.offset(posX, posY);
	}

	/**
	 * Laut Android Empfehlung sollen in der onDraw Methode aus Performance
	 * Gründen keine neuen Objekte angelegt werden, da diese Methode sehr oft
	 * aufgerufen wird. Deshalb legen wir die Matrix in einer Instanzvariablen
	 * ab, auch wenn
	 */
	private final Matrix transform = new Matrix();

	/**
	 * In dieser Methode behandeln wir die Touch Events die wir vom User
	 * erhalten. Der übergebene event enthält alle wichtigen Informationen wie
	 * Position, die Art der Action, etc.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {

		/*
		 * Als erstes übergeben wir unserem Skalierungs-Detektor den Event zur
		 * Auswertung.
		 */
		scaleDetector.onTouchEvent(event);

		switch (event.getAction() & MotionEvent.ACTION_MASK) {

		case MotionEvent.ACTION_DOWN: {

			/*
			 * Bei einem Action Down Event merken wir uns die Position, sofern
			 * diese innerhalb der Bounding Box liegt sowie die aktive Pointer
			 * ID.
			 */

			final float x = event.getX();
			final float y = event.getY();

			if (!boundingBox.contains(x, y)) {
				return false;
			}

			lastTouchX = x;
			lastTouchY = y;

			activePointerId = event.getPointerId(0);

			break;
		}

		case MotionEvent.ACTION_MOVE: {

			/*
			 * Beim Bewegen des Fingers müssen wir unterschiedliche Aktionen
			 * behandeln: entweder will der Benutzer das Objekt verschieben,
			 * rotieren oder zoomen. Rotieren und zoomen werden dabei zusammen
			 * behandelt, denn beim Rotieren kann das Bild gleichzeitig auch
			 * gezoomt werden.
			 */

			final int pointerIndex = event.findPointerIndex(activePointerId);

			final float x = event.getX(pointerIndex);
			final float y = event.getY(pointerIndex);

			boolean movingAround = !scaleDetector.isInProgress();

			if (movingAround) {
				final float dx = x - lastTouchX;
				final float dy = y - lastTouchY;

				/*
				 * Wir verschieben das Bild um die Differenz seit dem letzten
				 * Event.
				 */

				posX += dx;
				posY += dy;

			} else {

				/*
				 * Um das Zoomen kümmert sich der scaleDetector bereits, wir
				 * brauchen bloss noch die Rotation zu ermitteln.
				 */

				float r = getRotation(event);
				totalRotation += r - lastRotation;
				lastRotation = r;
			}

			lastTouchX = x;
			lastTouchY = y;

			/*
			 * Nach dem rotieren, zoomen oder skalieren müssen wir unsere View
			 * neu zeichnen, das veranlassen wir mit dem Aufruf von invalidate.
			 */
			invalidate();
			break;
		}

		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL: {

			/*
			 * Wird die Aktion abgebrochen vergessen wir den gemerkten Pointer
			 * wieder.
			 */

			activePointerId = INVALID_POINTER_ID;
			break;
		}

		/*
		 * Neben dem normalen primären Pointer gibt es auch noch den Action
		 * Pointer. So wird der zweite Finger genannte, der fürs Zoomen und
		 * Rotieren gebraucht wird.
		 */

		case MotionEvent.ACTION_POINTER_DOWN: {
			lastRotation = getRotation(event);
			break;
		}

		case MotionEvent.ACTION_POINTER_UP: {

			/*
			 * Einer der zwei Pointer hat den Screen verlassen. Falls dies unser
			 * activePointerId war, setzen wir den verbleibenden Finger als
			 * neuen activePointerId.
			 */

			final int indexOfPointerThatLeftTheScreen = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;

			if (event.getPointerId(indexOfPointerThatLeftTheScreen) == activePointerId) {
				final int newPointerIndex = indexOfPointerThatLeftTheScreen == 0 ? 1 : 0;
				lastTouchX = event.getX(newPointerIndex);
				lastTouchY = event.getY(newPointerIndex);
				activePointerId = event.getPointerId(newPointerIndex);
			}
			break;
		}
		}

		return true;
	}

	/**
	 * Bestimmt den Rotationswinkel in Grad aus dem MotionEvent.
	 */
	private float getRotation(MotionEvent event) {
		if (event.getPointerCount() >= 2) {
			double dX = event.getX(0) - event.getX(1);
			double dY = event.getY(0) - event.getY(1);
			return (float) Math.toDegrees(Math.atan2(dY, dX));
		} else {
			return 0;
		}
	}

}
