package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RecyclerViewContentScrollingInterface;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.asynctasks.AccountManagement;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.slidr.Slidr;
import ml.docilealligator.infinityforreddit.databinding.ActivityInboxBinding;
import ml.docilealligator.infinityforreddit.events.ChangeInboxCountEvent;
import ml.docilealligator.infinityforreddit.events.PassPrivateMessageEvent;
import ml.docilealligator.infinityforreddit.events.PassPrivateMessageIndexEvent;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.fragments.InboxFragment;
import ml.docilealligator.infinityforreddit.message.FetchMessage;
import ml.docilealligator.infinityforreddit.message.Message;
import ml.docilealligator.infinityforreddit.thing.SelectThingReturnKey;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class InboxActivity extends BaseActivity implements ActivityToolbarInterface, RecyclerViewContentScrollingInterface {

    public static final String EXTRA_NEW_ACCOUNT_NAME = "ENAN";
    public static final String EXTRA_VIEW_MESSAGE = "EVM";

    private static final String NEW_ACCOUNT_NAME_STATE = "NANS";
    private static final int SEARCH_USER_REQUEST_CODE = 1;

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private FragmentManager fragmentManager;
    private String mNewAccountName;
    private ActivityInboxBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);

        binding = ActivityInboxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        if (mSharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_RIGHT_TO_GO_BACK, true)) {
            mSliderPanel = Slidr.attach(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutInboxActivity);
            }

            if (isImmersiveInterface()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }

                ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                        Insets allInsets = insets.getInsets(
                                WindowInsetsCompat.Type.systemBars()
                                        | WindowInsetsCompat.Type.displayCutout()
                        );

                        setMargins(binding.toolbarInboxActivity,
                                allInsets.left,
                                allInsets.top,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        binding.viewPagerInboxActivity.setPadding(
                                allInsets.left,
                                0,
                                allInsets.right,
                                allInsets.bottom);

                        setMargins(binding.fabInboxActivity,
                                BaseActivity.IGNORE_MARGIN,
                                BaseActivity.IGNORE_MARGIN,
                                (int) Utils.convertDpToPixel(16, InboxActivity.this) + allInsets.right,
                                (int) Utils.convertDpToPixel(16, InboxActivity.this) + allInsets.bottom);

                        setMargins(binding.tabLayoutInboxActivity,
                                allInsets.left,
                                BaseActivity.IGNORE_MARGIN,
                                allInsets.right,
                                BaseActivity.IGNORE_MARGIN);

                        return WindowInsetsCompat.CONSUMED;
                    }
                });

                /*adjustToolbar(binding.toolbarInboxActivity);

                int navBarHeight = getNavBarHeight();
                if (navBarHeight > 0) {
                    CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) binding.fabInboxActivity.getLayoutParams();
                    params.bottomMargin += navBarHeight;
                    binding.fabInboxActivity.setLayoutParams(params);
                }*/
            }
        }

        binding.toolbarInboxActivity.setTitle(R.string.inbox);
        setSupportActionBar(binding.toolbarInboxActivity);
        setToolbarGoToTop(binding.toolbarInboxActivity);

        fragmentManager = getSupportFragmentManager();

        if (savedInstanceState != null) {
            mNewAccountName = savedInstanceState.getString(NEW_ACCOUNT_NAME_STATE);
        } else {
            mNewAccountName = getIntent().getStringExtra(EXTRA_NEW_ACCOUNT_NAME);
        }

        sectionsPagerAdapter = new SectionsPagerAdapter(this);

        getCurrentAccountAndFetchMessage(savedInstanceState);

        binding.viewPagerInboxActivity.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.fabInboxActivity.show();
            }
        });

        binding.fabInboxActivity.setOnClickListener(view -> {
            View rootView = getLayoutInflater().inflate(R.layout.dialog_go_to_thing_edit_text, binding.getRoot(), false);
            TextInputEditText thingEditText = rootView.findViewById(R.id.text_input_edit_text_go_to_thing_edit_text);
            thingEditText.requestFocus();
            Utils.showKeyboard(this, new Handler(), thingEditText);
            thingEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    Utils.hideKeyboard(this);
                    Intent pmIntent = new Intent(this, SendPrivateMessageActivity.class);
                    pmIntent.putExtra(SendPrivateMessageActivity.EXTRA_RECIPIENT_USERNAME, thingEditText.getText().toString());
                    startActivity(pmIntent);
                    return true;
                }
                return false;
            });
            new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.choose_a_user)
                    .setView(rootView)
                    .setPositiveButton(R.string.ok, (dialogInterface, i)
                            -> {
                        Utils.hideKeyboard(this);
                        Intent pmIntent = new Intent(this, SendPrivateMessageActivity.class);
                        pmIntent.putExtra(SendPrivateMessageActivity.EXTRA_RECIPIENT_USERNAME, thingEditText.getText().toString());
                        startActivity(pmIntent);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.search, (dialogInterface, i) -> {
                        Utils.hideKeyboard(this);
                        Intent intent = new Intent(this, SearchActivity.class);
                        intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true);
                        startActivityForResult(intent, SEARCH_USER_REQUEST_CODE);
                    })
                    .setOnDismissListener(dialogInterface -> {
                        Utils.hideKeyboard(this);
                    })
                    .show();
        });
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        binding.getRoot().setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutInboxActivity,
                binding.collapsingToolbarLayoutInboxActivity, binding.toolbarInboxActivity);
        applyTabLayoutTheme(binding.tabLayoutInboxActivity);
        applyFABTheme(binding.fabInboxActivity);
    }

    private void getCurrentAccountAndFetchMessage(Bundle savedInstanceState) {
        if (mNewAccountName != null) {
            if (accountName.equals(Account.ANONYMOUS_ACCOUNT) || !accountName.equals(mNewAccountName)) {
                AccountManagement.switchAccount(mRedditDataRoomDatabase, mCurrentAccountSharedPreferences,
                        mExecutor, new Handler(), mNewAccountName, newAccount -> {
                            EventBus.getDefault().post(new SwitchAccountEvent(getClass().getName()));
                            Toast.makeText(this, R.string.account_switched, Toast.LENGTH_SHORT).show();

                            mNewAccountName = null;
                            if (newAccount != null) {
                                accessToken = newAccount.getAccessToken();
                                accountName = newAccount.getAccountName();
                            }

                            bindView(savedInstanceState);
                        });
            } else {
                bindView(savedInstanceState);
            }
        } else {
            bindView(savedInstanceState);
        }
    }

    private void bindView(Bundle savedInstanceState) {
        binding.viewPagerInboxActivity.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    unlockSwipeRightToGoBack();
                } else {
                    lockSwipeRightToGoBack();
                }
            }
        });
        binding.viewPagerInboxActivity.setAdapter(sectionsPagerAdapter);
        new TabLayoutMediator(binding.tabLayoutInboxActivity, binding.viewPagerInboxActivity, (tab, position) -> {
            switch (position) {
                case 0:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.notifications));
                    break;
                case 1:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.messages));
                    break;
            }
        }).attach();
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_VIEW_MESSAGE, false)) {
            binding.viewPagerInboxActivity.setCurrentItem(1, false);
        }

        fixViewPager2Sensitivity(binding.viewPagerInboxActivity);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.inbox_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_refresh_inbox_activity) {
            if (sectionsPagerAdapter != null) {
                sectionsPagerAdapter.refresh();
            }
            return true;
        } else if (item.getItemId() == R.id.action_read_all_messages_inbox_activity) {
            if (!accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                Toast.makeText(this, R.string.please_wait, Toast.LENGTH_SHORT).show();
                mOauthRetrofit.create(RedditAPI.class).readAllMessages(APIUtils.getOAuthHeader(accessToken))
                        .enqueue(new Callback<>() {
                            @Override
                            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(InboxActivity.this, R.string.read_all_messages_success, Toast.LENGTH_SHORT).show();
                                    if (sectionsPagerAdapter != null) {
                                        sectionsPagerAdapter.readAllMessages();
                                    }
                                    EventBus.getDefault().post(new ChangeInboxCountEvent(0));
                                } else {
                                    if (response.code() == 429) {
                                        Toast.makeText(InboxActivity.this, R.string.read_all_messages_time_limit, Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(InboxActivity.this, R.string.read_all_messages_failed, Toast.LENGTH_LONG).show();
                                    }
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                                Toast.makeText(InboxActivity.this, R.string.read_all_messages_failed, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        } else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == SEARCH_USER_REQUEST_CODE && data != null) {
            String username = data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME);
            Intent intent = new Intent(this, SendPrivateMessageActivity.class);
            intent.putExtra(SendPrivateMessageActivity.EXTRA_RECIPIENT_USERNAME, username);
            startActivity(intent);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(NEW_ACCOUNT_NAME_STATE, mNewAccountName);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        if (!getClass().getName().equals(event.excludeActivityClassName)) {
            finish();
        }
    }

    @Subscribe
    public void onPassPrivateMessageIndexEvent(PassPrivateMessageIndexEvent event) {
        if (sectionsPagerAdapter != null) {
            EventBus.getDefault().post(new PassPrivateMessageEvent(sectionsPagerAdapter.getPrivateMessage(event.privateMessageIndex)));
        }
    }

    @Override
    public void onLongPress() {
        if (sectionsPagerAdapter != null) {
            sectionsPagerAdapter.goBackToTop();
        }
    }

    @Override
    public void lockSwipeRightToGoBack() {
        if (mSliderPanel != null) {
            mSliderPanel.lock();
        }
    }

    @Override
    public void unlockSwipeRightToGoBack() {
        if (mSliderPanel != null) {
            mSliderPanel.unlock();
        }
    }

    @Override
    public void contentScrollUp() {
        binding.fabInboxActivity.show();
    }

    @Override
    public void contentScrollDown() {
        binding.fabInboxActivity.hide();
    }

    private class SectionsPagerAdapter extends FragmentStateAdapter {

        SectionsPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Nullable
        private Fragment getCurrentFragment() {
            if (fragmentManager == null) {
                return null;
            }
            return fragmentManager.findFragmentByTag("f" + binding.viewPagerInboxActivity.getCurrentItem());
        }

        void refresh() {
            InboxFragment fragment = (InboxFragment) getCurrentFragment();
            if (fragment != null) {
                fragment.refresh();
            }
        }

        void goBackToTop() {
            InboxFragment fragment = (InboxFragment) getCurrentFragment();
            if (fragment != null) {
                fragment.goBackToTop();
            }
        }

        void readAllMessages() {
            InboxFragment fragment = (InboxFragment) getCurrentFragment();
            if (fragment != null) {
                fragment.markAllMessagesRead();
            }
        }

        Message getPrivateMessage(int index) {
            if (fragmentManager == null) {
                return null;
            }
            Fragment fragment = fragmentManager.findFragmentByTag("f" + binding.viewPagerInboxActivity.getCurrentItem());
            if (fragment instanceof InboxFragment) {
                return ((InboxFragment) fragment).getMessageByIndex(index);
            }
            return null;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                InboxFragment fragment = new InboxFragment();
                Bundle bundle = new Bundle();
                bundle.putString(InboxFragment.EXTRA_MESSAGE_WHERE, FetchMessage.WHERE_INBOX);
                fragment.setArguments(bundle);
                return fragment;
            } else {
                InboxFragment fragment = new InboxFragment();
                Bundle bundle = new Bundle();
                bundle.putString(InboxFragment.EXTRA_MESSAGE_WHERE, FetchMessage.WHERE_MESSAGES);
                fragment.setArguments(bundle);
                return fragment;
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}
