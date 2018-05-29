// Ref: https://gist.github.com/JMPergar/439aaa3249fa184c7c0c

package tw.cchi.medthimager.component;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ScrollView;

import tw.cchi.medthimager.Config;
import tw.cchi.medthimager.R;

public class ScrollViewWithMaxHeight extends ScrollView {
    private final String TAG = Config.TAGPRE + getClass().getSimpleName();

    public static int WITHOUT_MAX_HEIGHT_VALUE = -1;

    private int maxHeight = WITHOUT_MAX_HEIGHT_VALUE;

    public ScrollViewWithMaxHeight(Context context) {
        super(context);
    }

    public ScrollViewWithMaxHeight(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ScrollViewWithMaxHeight,
                0, 0);

        try {
            setMaxHeight((int) a.getDimension(R.styleable.ScrollViewWithMaxHeight_maxHeight, 0));

        } finally {
            a.recycle();
        }
    }

    public ScrollViewWithMaxHeight(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            if (maxHeight != WITHOUT_MAX_HEIGHT_VALUE && heightSize > maxHeight) {
                heightSize = maxHeight;
            }
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST);
            getLayoutParams().height = heightSize;
        } catch (Exception e) {
            Log.e(TAG, "onMeasure: Error forcing height.");
        } finally {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

}
