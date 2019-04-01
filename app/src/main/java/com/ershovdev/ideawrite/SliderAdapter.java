package com.ershovdev.ideawrite;

import android.content.Context;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SliderAdapter extends PagerAdapter {

    private Context context;
    private LayoutInflater layoutInflater;

    private String[] headings = {
            "Гипотеза",
            "Действие",
            "Метрика",
            "Результат",
            "Деньги"
    };

    private String[] under_headings = {
            "Что за идея?",
            "Что нужно сделать?",
            "На какой показатель влияет?",
            "Что из этого получится?",
            "Сколько заработаешь?"
    };

    public SliderAdapter(Context context) {
        this.context = context;
    }

    @Override
    public int getCount() {
        return 5;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
        return view == o;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        int layout = (position < 4) ? R.layout.slider_layout : R.layout.slider_layout_for_money;

        View view = layoutInflater.inflate(layout, container, false);

        TextView heading = view.findViewById(R.id.heading);
        TextView under_heading = view.findViewById(R.id.under_heading);

        heading.setText(headings[position]);
        under_heading.setText(under_headings[position]);

        if (position == 4) {
            TextView under_heading_2 = view.findViewById(R.id.under_heading_2);
            under_heading_2.setText("Сколько можешь потерять?");
        }

        container.addView(view);

        return view;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((LinearLayout) object);
    }

    @Nullable
    @Override
    public Parcelable saveState() {
        return super.saveState();
    }
}
