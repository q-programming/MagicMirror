package pl.qprogramming.magicmirror.service;

import android.service.dreams.DreamService;
import android.widget.ImageView;

import pl.qprogramming.magicmirror.R;

public class HomeDreamService extends DreamService {

    @Override
    public void onAttachedToWindow () {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(R.layout.activity_home);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
    }
}
