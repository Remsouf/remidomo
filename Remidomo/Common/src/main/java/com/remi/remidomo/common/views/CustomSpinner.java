package com.remi.remidomo.common.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.remi.remidomo.common.R;

public class CustomSpinner extends View {

	private final static int PICKER_SIZE = 300;
	private final static int CIRCLE_THICKNESS = 40;
	private final static int KNOB_RADIUS = (CIRCLE_THICKNESS / 2) - 2;
	private final static int TEXT_SIZE = 45;
	private final static float SPEED_FACTOR = 0.025f;

	private int drawWidth;
	private int drawHeight;

	// Current values
	private double value;
	private double angle;

	// Proxies
	private int circleRadius;

	// Boundaries
	private int min=1;
	private int max=100;

	private Paint paint;

	// Resource id for the related EditText
	private int linkedEditTextId;
	private EditText linkedEditText;

	// Watcher for the optional EditText
	private TextView.OnEditorActionListener editListener = new TextView.OnEditorActionListener() {
	    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
	        if (actionId == EditorInfo.IME_ACTION_DONE) {
	        	try {
					value = Integer.parseInt(v.getText().toString());
					value = Math.max(value, min);
					value = Math.min(value, max);

					// Update in case of boundary fix
					if (!String.valueOf(getCurrent()).equals(v.getText().toString())) {
						updateEditText();
						return true;
					}
				} catch (NumberFormatException ignored) {}
	        }
	        return false;
	    }
	};

	public CustomSpinner(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.CustomSpinner);
		linkedEditTextId = a.getResourceId(R.styleable.CustomSpinner_linked_edit_text, -1);
		a.recycle();

		initSpinner();
	}

	private void initSpinner() {
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setTextAlign(Paint.Align.CENTER);
		paint.setTextSize(TEXT_SIZE);
	}

	public void setMinimum(int min) {
		this.min = min;
	}

	public void setMaximum(int max) {
		this.max = max;
	}

	public int getCurrent() {
		return (int)Math.round(value);
	}

	public void setCurrent(int value) {
		this.value = value;
		updateEditText();
		requestLayout();
		invalidate();
	}

	private void updateEditText() {
		if (linkedEditText != null) {
			linkedEditText.setText(String.valueOf((int)Math.round(value)));
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(measureWidthDimension(widthMeasureSpec),
				measureHeightDimension(heightMeasureSpec));
	}

	private int measureWidthDimension(int measureSpec) {
		int result = 0;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			// Measure the text
			result = (int) PICKER_SIZE + getPaddingLeft() + getPaddingRight();
			if (specMode == MeasureSpec.AT_MOST) {
				// Respect AT_MOST value if that was what is called for by measureSpec
				result = Math.min(result, specSize);
			}
		}

		return result;
	}

	private int measureHeightDimension(int measureSpec) {
		int result = 0;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			// Measure the text
			result = (int) PICKER_SIZE + getPaddingTop() + getPaddingBottom();
			if (specMode == MeasureSpec.AT_MOST) {
				// Respect AT_MOST value if that was what is called for by measureSpec
				result = Math.min(result, specSize);
			}
		}

		return result;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		drawWidth = w;
		drawHeight = h;
		super.onSizeChanged(w, h, oldw, oldh);

		/* Take the opportunity to establish link with EditText */
		RelativeLayout parent = (RelativeLayout) getParent();
		linkedEditText = (EditText) parent.findViewById(linkedEditTextId);
		if (linkedEditText != null) {
			linkedEditText.setOnEditorActionListener(editListener);
			updateEditText();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {      

		//String centralText = String.valueOf((int)value);
		int minSize = Math.min(drawWidth,  drawHeight);

		//paint.getTextBounds(centralText, 0, centralText.length(), textBounds);

		// Outer circle
		circleRadius = (minSize-CIRCLE_THICKNESS)/2;
		paint.setColor(Color.WHITE);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(CIRCLE_THICKNESS);
		int[] colors = new int[2];
		colors[0] = Color.parseColor("#FFFFFFFF");
		colors[1] = Color.parseColor("#FF111111");
		Shader shader = new LinearGradient(drawWidth/2, drawHeight/2, drawWidth, drawHeight, colors, null, Shader.TileMode.MIRROR);
		paint.setShader(shader);
		canvas.drawCircle(drawWidth/2, drawHeight/2, circleRadius, paint);

		// Knob
		paint.setColor(Color.BLACK);
		paint.setStyle(Paint.Style.FILL);
		int x = drawWidth/2 + (int)(circleRadius*Math.cos(angle));
		int y = drawHeight/2 + (int)(circleRadius*Math.sin(angle));
		colors[0] = Color.parseColor("#FF666666");
		colors[1] = Color.parseColor("#FFEEEEEE");
		float[] positions = new float[2];
		positions[0] = 0.0f;
		positions[1] = 0.6f;
		shader = new RadialGradient(x, y-KNOB_RADIUS, KNOB_RADIUS*2, colors, positions, Shader.TileMode.CLAMP);
		paint.setShader(shader);
		canvas.drawCircle(x, y, KNOB_RADIUS, paint);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if ((event.getAction() != MotionEvent.ACTION_DOWN) &&
				(event.getAction() != MotionEvent.ACTION_MOVE)) {
			return true;
		}

		int distanceFromCenterX = (int)(event.getX() - drawWidth/2);
		int distanceFromCenterY = (int)(event.getY() - drawHeight/2);
		int radius = (int)Math.sqrt(distanceFromCenterX*distanceFromCenterX +
				distanceFromCenterY*distanceFromCenterY);

		// Touched in jog circle ?
		if (Math.abs(radius - circleRadius) <= CIRCLE_THICKNESS) {
			// Compute angle
			double angleCos = Math.acos((double)distanceFromCenterX / radius);
			double angleSin = Math.asin((double)distanceFromCenterY / radius);
			double newAngle;
			if (angleSin >= 0) {
				newAngle =  angleCos;
			} else {
				newAngle = -angleCos + 2*Math.PI;
			}

			// Compute value (only if moving around)
			if (event.getAction() == MotionEvent.ACTION_MOVE) {
				double crossProduct = Math.sin(newAngle - angle);

				// Don't update value if change is too small
				if (Math.abs(crossProduct) > 0.005) {

					double increment = ((max-min)*Math.abs(crossProduct)*SPEED_FACTOR);

					// If movement is small, force 1 increment
					// (if increment is < 1, keep it)
					if ((Math.abs(crossProduct) < 0.2) && (increment > 1)) {
						increment = 1;
					}

					value = value + increment*Math.signum(crossProduct);
					value = Math.max(value, min);
					value = Math.min(value, max);
				}
			}

			angle = newAngle;

			updateEditText();
			invalidate();
			return true;
		} else {
			return false;
		}
	}
}