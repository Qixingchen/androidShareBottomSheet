package moe.xing.share;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetDialog;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.jakewharton.rxbinding.view.RxView;
import com.tencent.mm.sdk.modelmsg.SendMessageToWX;
import com.tencent.mm.sdk.modelmsg.WXMediaMessage;
import com.tencent.mm.sdk.modelmsg.WXWebpageObject;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import moe.xing.baseutils.utils.IntentUtils;
import moe.xing.baseutils.utils.LogHelper;
import moe.xing.mvp_utils.BaseActivity;
import moe.xing.rvutils.BaseRecyclerViewAdapter;
import moe.xing.share.databinding.ItemShareToAppsBinding;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by Qi Xingchen on 16-11-18.
 * <p>
 * 底部分享栏
 */

@SuppressWarnings("unused")
public class ShareBottomSheet {

    @NonNull
    private Activity mActivity;

    private IWXAPI api;

    private BottomSheetDialog mDialog;

    private boolean onlyTencent = false;

    private ShareBottomSheet(@NonNull Activity activity, @NonNull String WechatID, boolean onlyTecent) {
        mActivity = activity;
        mDialog = new BottomSheetDialog(activity);
        mDialog.setTitle("分享");
        api = WXAPIFactory.createWXAPI(mActivity, WechatID, false);
        api.registerApp(WechatID);
        this.onlyTencent = onlyTecent;
    }

    public static ShareBottomSheet getNewInstance(@NonNull Activity activity, @NonNull String WechatID, boolean onlyTecent) {
        return new ShareBottomSheet(activity, WechatID, onlyTecent);
    }

    public void ShareWeb(String title, String url, String thumbImageUrl, String desc) {
        PackageManager pm = mActivity.getPackageManager();
        Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, title + "  " + url);
        List<ResolveInfo> installedApps = IntentUtils.getIntentAppIcon(shareIntent);
        List<ShareApps> shareApps = new ArrayList<>();
        ShareApps s1 = new ShareApps();
        s1.name = "系统分享";
        s1.icon = ContextCompat.getDrawable(mActivity, R.drawable.ic_share_24dp);
        shareApps.add(s1);
        boolean wechatAdded = false;
        for (ResolveInfo info : installedApps) {
            String packageName = info.activityInfo.packageName;
            if (packageName.contains("com.tencent") || !onlyTencent) {

                ShareApps app = new ShareApps();
                app.info = info;
                app.name = info.activityInfo.loadLabel(pm).toString();
                app.icon = info.activityInfo.loadIcon(pm);
                shareApps.add(app);

                if (app.info.activityInfo.packageName.startsWith("com.tencent.mm") && !wechatAdded) {
                    wechatAdded = true;
                    ShareApps wechat = new ShareApps();
                    Parcel p = Parcel.obtain();
                    info.writeToParcel(p, 0);
                    p.setDataPosition(0);

                    wechat.info = ResolveInfo.CREATOR.createFromParcel(p);
                    assert wechat.info != null;
                    wechat.info.activityInfo.name = "朋友圈";
                    wechat.name = "朋友圈";
                    wechat.icon = ContextCompat.getDrawable(mActivity, R.drawable.ic_wechat_timeline);
                    shareApps.add(wechat);
                }
            }
        }

        setDialog(shareApps, shareWebListener(shareIntent, title, url, thumbImageUrl, desc));
//        this.url = pkgAppsList.get(0).activityInfo.name;
//        icon = pkgAppsList.get(0).activityInfo.loadIcon( pm );
//        this.displayName = pkgAppsList.get(0).activityInfo.loadLabel( pm ).toString();
//        this.module = pkgAppsList.get(0).activityInfo.packageName;
//        this.isExternal = true;
//        this.count = count;
        mDialog.show();
    }

    private Adapter.ShareListener shareWebListener(final Intent intent, final String title,
                                                   final String url, final String thumbImage, final String desc) {
        return new Adapter.ShareListener() {
            @Override
            public void toShare(final ShareApps app) {
                mDialog.dismiss();
                if ("系统分享".equals(app.name)) {
                    IntentUtils.startIntent(intent);
                    return;
                }
                if (app.info.activityInfo.packageName.startsWith("com.tencent.mm")) {
                    ((BaseActivity) mActivity).showProgressDialog();
                    WXWebpageObject webpage = new WXWebpageObject();
                    webpage.webpageUrl = url;
                    final WXMediaMessage msg = new WXMediaMessage(webpage);
                    msg.title = title;
                    msg.description = desc;

                    Observable.create(new Observable.OnSubscribe<Bitmap>() {
                        @Override
                        public void call(Subscriber<? super Bitmap> subscriber) {
                            try {
                                subscriber.onNext(Glide.with(mActivity).load(thumbImage)
                                        .asBitmap().centerCrop()
                                        .into(100, 100).get());
                            } catch (Exception e) {
                                e.printStackTrace();
                                subscriber.onError(e);
                            }

                        }
                    }).subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new Subscriber<Bitmap>() {
                                @Override
                                public void onCompleted() {
                                }

                                @Override
                                public void onError(Throwable e) {
                                    LogHelper.Toast(e.getLocalizedMessage());
                                }

                                @Override
                                public void onNext(Bitmap bitmap) {

                                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 30, stream);
                                    msg.thumbData = stream.toByteArray();

                                    SendMessageToWX.Req req = new SendMessageToWX.Req();
                                    req.transaction = "2333";
                                    req.message = msg;


                                    if (app.info.activityInfo.name.toLowerCase().contains("addFavor".toLowerCase())) {
                                        req.scene = SendMessageToWX.Req.WXSceneFavorite;
                                    } else if (app.info.activityInfo.name.toLowerCase().contains("share")) {
                                        req.scene = SendMessageToWX.Req.WXSceneSession;
                                    } else if (app.info.activityInfo.name.contains("朋友圈")) {
                                        req.scene = SendMessageToWX.Req.WXSceneTimeline;
                                    }
                                    api.sendReq(req);
                                }
                            });
                    return;
                }
                final ActivityInfo activity = app.info.activityInfo;
                final ComponentName name = new ComponentName(activity.applicationInfo.packageName, activity.name);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.setComponent(name);
                IntentUtils.startIntent(intent);
            }
        };
    }

    private void setDialog(List<ShareApps> shareApps, Adapter.ShareListener listener) {
        RecyclerView recyclerView = new RecyclerView(mActivity);
        recyclerView.setLayoutManager(new GridLayoutManager(mActivity, 3));
        Adapter adapter = new Adapter(listener);
        adapter.addData(shareApps);
        recyclerView.setAdapter(adapter);
        mDialog.setContentView(recyclerView);
    }

    static class Adapter extends BaseRecyclerViewAdapter<ShareApps, Adapter.ViewHolder> {

        @NonNull
        private ShareListener mShareListener;

        Adapter(@NonNull ShareListener shareListener) {
            super(ShareApps.class);
            mShareListener = shareListener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ItemShareToAppsBinding binding = DataBindingUtil.inflate(
                    LayoutInflater.from(parent.getContext()), R.layout.item_share_to_apps, parent, false);
            return new ViewHolder(binding.getRoot());
        }


        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mBinding.setShare(datas.get(position));
            RxView.clicks(holder.itemView).throttleFirst(1, TimeUnit.SECONDS)
                    .subscribe(new Action1<Void>() {
                        @Override
                        public void call(Void aVoid) {
                            mShareListener.toShare(datas.get(holder.getAdapterPosition()));
                        }
                    });
        }

        interface ShareListener {
            void toShare(ShareApps app);
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private ItemShareToAppsBinding mBinding;

            ViewHolder(View itemView) {
                super(itemView);
                mBinding = DataBindingUtil.findBinding(itemView);
            }
        }
    }

    public class ShareApps {
        public ResolveInfo info;
        public Drawable icon;
        public String name;
    }
}
