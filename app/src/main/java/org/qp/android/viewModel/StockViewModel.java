package org.qp.android.viewModel;

import static org.qp.android.utils.DirUtil.doesDirectoryContainGameFiles;
import static org.qp.android.utils.FileUtil.GAME_INFO_FILENAME;
import static org.qp.android.utils.FileUtil.copyFile;
import static org.qp.android.utils.FileUtil.createFile;
import static org.qp.android.utils.FileUtil.dirSize;
import static org.qp.android.utils.FileUtil.findFileOrDirectory;
import static org.qp.android.utils.FileUtil.findFileRecursively;
import static org.qp.android.utils.FileUtil.formatFileSize;
import static org.qp.android.utils.FileUtil.getOrCreateDirectory;
import static org.qp.android.utils.FileUtil.isWritableDirectory;
import static org.qp.android.utils.FileUtil.isWritableFile;
import static org.qp.android.utils.PathUtil.normalizeFolderName;
import static org.qp.android.utils.PathUtil.removeExtension;
import static org.qp.android.utils.XmlUtil.objectToXml;

import android.Manifest;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.Contract;
import org.qp.android.GameDataParcel;
import org.qp.android.QuestPlayerApplication;
import org.qp.android.R;
import org.qp.android.databinding.DialogEditBinding;
import org.qp.android.databinding.DialogInstallBinding;
import org.qp.android.dto.stock.GameData;
import org.qp.android.model.install.Installer;
import org.qp.android.model.notify.NotifyBuilder;
import org.qp.android.model.plugin.PluginClient;
import org.qp.android.model.plugin.PluginType;
import org.qp.android.plugin.AsyncCallback;
import org.qp.android.utils.ArchiveUtil;
import org.qp.android.view.game.GameActivity;
import org.qp.android.view.settings.SettingsController;
import org.qp.android.view.stock.StockActivity;
import org.qp.android.view.stock.fragment.dialogs.StockDialogFrags;
import org.qp.android.view.stock.fragment.dialogs.StockDialogType;
import org.qp.android.viewModel.repository.LocalGame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class StockViewModel extends AndroidViewModel {
    private final String TAG = this.getClass().getSimpleName();

    public ObservableField<StockActivity> activityObservableField = new
            ObservableField<>();

    public ObservableBoolean isShowDialog = new ObservableBoolean();
    public ObservableBoolean isSelectArchive = new ObservableBoolean();
    public ObservableBoolean isSelectFolder = new ObservableBoolean();

    private final LocalGame localGame = new LocalGame();
    private final HashMap<String, GameData> gamesMap = new HashMap<>();

    private File gamesDir;
    private DocumentFile tempInstallFile, tempInstallDir, tempImageFile, tempPathFile, tempModFile;

    private DialogInstallBinding installBinding;
    private GameData tempGameData;
    private DialogEditBinding editBinding;
    private SettingsController controller;

    private final MutableLiveData<ArrayList<GameData>> gameDataList;

    public MutableLiveData<Integer> outputIntObserver = new MutableLiveData<>();

    // region Getter/Setter
    public void setController(SettingsController controller) {
        this.controller = controller;
    }

    public void setTempGameData(GameData tempGameData) {
        this.tempGameData = tempGameData;
    }

    public void setTempPathFile(DocumentFile tempPathFile) {
        this.tempPathFile = tempPathFile;
        editBinding.buttonSelectPath.setText(tempPathFile.getName());
    }

    public void setTempModFile(DocumentFile tempModFile) {
        this.tempModFile = tempModFile;
        editBinding.buttonSelectMod.setText(tempModFile.getName());
    }

    public void setTempInstallFile(@NonNull DocumentFile tempInstallFile) {
        this.tempInstallFile = tempInstallFile;
        installBinding.buttonSelectArchive.setText(tempInstallFile.getName());
    }

    public void setTempInstallDir(@NonNull DocumentFile tempInstallDir) {
        this.tempInstallDir = tempInstallDir;
        installBinding.buttonSelectArchive.setEnabled(false);
        installBinding.buttonSelectFolder.setText(tempInstallDir.getName());
    }

    public void setTempImageFile(@NonNull DocumentFile tempImageFile) {
        this.tempImageFile = tempImageFile;
        if (installBinding != null) {
            installBinding.buttonSelectIcon.setText(tempImageFile.getName());
            Picasso.get()
                    .load(tempImageFile.getUri())
                    .fit()
                    .into(installBinding.imageView);
        }
        if (editBinding != null) {
            editBinding.buttonSelectIcon.setText(tempImageFile.getName());
            Picasso.get()
                    .load(tempImageFile.getUri())
                    .fit()
                    .into(editBinding.imageView);
        }
    }

    public void setGamesDir(File gamesDir) {
        this.gamesDir = gamesDir;
    }

    public void setGameDataList(ArrayList<GameData> gameDataArrayList) {
        gameDataList.postValue(gameDataArrayList);
    }

    public void setLocalGameDataList () {
        var gameData = getSortedGames();
        var localGameData = new ArrayList<GameData>();
        for (var data : gameData) {
            if (data.isInstalled()) {
                localGameData.add(data);
            }
        }
        setGameDataList(localGameData);
    }

    @Nullable
    private StockActivity getStockActivity() {
        return activityObservableField.get();
    }

    public String getGameIdByPosition(int position) {
        getGameData().observeForever(gameDataArrayList -> {
            if (!gameDataArrayList.isEmpty() && gameDataArrayList.size() > position) {
                setTempGameData(gameDataArrayList.get(position));
            }
        });
        return getTempGameData().id;
    }

    @NonNull
    public ArrayList<GameData> getSortedGames() {
        var unsortedGameData = gamesMap.values();
        var gameData = new ArrayList<>(unsortedGameData);
        if (gameData.size() < 2) return gameData;
        gameData.sort(Comparator.comparing(game -> game.title.toLowerCase()));
        return gameData;
    }

    public LiveData<ArrayList<GameData>> getGameData() {
        if (gameDataList.getValue() != null) {
            var application = (QuestPlayerApplication) getApplication();
            application.setGameList(gameDataList.getValue());
        }
        return gameDataList;
    }

    public GameData getTempGameData() {
        return tempGameData;
    }

    public HashMap<String, GameData> getGamesMap() {
        return gamesMap;
    }

    public String getGameAuthor () {
        if (tempGameData.author.length() > 0
                && getStockActivity() != null) {
            return getStockActivity()
                    .getString(R.string.author)
                    .replace("-AUTHOR-" , tempGameData.author);
        } else {
            return "";
        }
    }

    public String getGamePortBy () {
        if (tempGameData.portedBy.length() > 0
                && getStockActivity() != null) {
            return getStockActivity()
                    .getString(R.string.ported_by)
                    .replace("-PORTED_BY-", tempGameData.portedBy);
        } else {
            return "";
        }
    }

    public String getGameVersion () {
        if (tempGameData.version.length() > 0
                && getStockActivity() != null) {
            return getStockActivity()
                    .getString(R.string.version)
                    .replace("-VERSION-" , tempGameData.version);
        } else {
            return "";
        }
    }

    public String getGameType () {
        if (tempGameData.fileExt.length() > 0
                && getStockActivity() != null) {
            if (tempGameData.fileExt.equals("aqsp")) {
                return getStockActivity()
                        .getString(R.string.fileType)
                        .replace("-TYPE-", tempGameData.fileExt)
                        + " " + getStockActivity().getString(R.string.experimental);
            }
            return getStockActivity()
                    .getString(R.string.fileType)
                    .replace("-TYPE-", tempGameData.fileExt);
        } else {
            return "";
        }
    }

    public String getGameSize () {
        if (tempGameData.getFileSize() != null
                && getStockActivity() != null) {
            return getStockActivity()
                    .getString(R.string.fileSize)
                    .replace("-SIZE-" , tempGameData.getFileSize());
        } else {
            return "";
        }
    }

    public String getGamePubData () {
        if (tempGameData.pubDate.length() > 0
                && getStockActivity() != null) {
            return getStockActivity()
                    .getString(R.string.pub_data)
                    .replace("-PUB_DATA-", tempGameData.pubDate);
        } else {
            return "";
        }
    }

    public String getGameModData () {
        if (tempGameData.modDate.length() > 0
                && getStockActivity() != null) {
            return getStockActivity()
                    .getString(R.string.mod_data)
                    .replace("-MOD_DATA-", tempGameData.pubDate);
        } else {
            return "";
        }
    }

    public SettingsController getSettingsController() {
        return SettingsController.newInstance(getApplication());
    }

    public boolean isGameInstalled () {
        return tempGameData.isInstalled() && doesDirectoryContainGameFiles(tempGameData.gameDir);
    }

    public boolean isHasRemoteUrl () {
        return tempGameData.hasRemoteUrl();
    }

    public boolean isModsDirExist() {
        return findFileRecursively(tempGameData.gameDir, "mods") != null;
    }
    // endregion Getter/Setter

    public StockViewModel(@NonNull Application application) {
        super(application);
        gameDataList = new MutableLiveData<>();
    }

    // region Dialog
    private StockDialogFrags dialogFragments = new StockDialogFrags();

    public void showDialogInstall() {
        dialogFragments.setDialogType(StockDialogType.INSTALL_DIALOG);
        dialogFragments.setInstallBinding(formingInstallView());
        dialogFragments.onCancel(new DialogInterface() {
            @Override
            public void cancel() {
                isShowDialog.set(false);
            }

            @Override
            public void dismiss() {
            }
        });
        Objects.requireNonNull(activityObservableField.get())
                .showInstallDialogFragment(dialogFragments);
        isShowDialog.set(true);
    }

    private String passwordForArchive;

    public void createInstallIntent() {
        var gameData = new GameData();
        try {
            gameData.id = removeExtension(Objects.requireNonNull(tempInstallFile != null ?
                    tempInstallFile.getName()
                    : tempInstallDir.getName()));
            gameData.title = (Objects.requireNonNull(
                    installBinding.ET0.getEditText()).getText().toString().isEmpty() ?
                    removeExtension(Objects.requireNonNull(tempInstallFile != null ?
                            tempInstallFile.getName() :
                            tempInstallDir.getName()))
                    : Objects.requireNonNull(
                    installBinding.ET0.getEditText()).getText().toString());
            gameData.author = (Objects.requireNonNull(
                    installBinding.ET1.getEditText()).getText().toString().isEmpty() ?
                    null
                    : Objects.requireNonNull(
                    installBinding.ET1.getEditText()).getText().toString());
            gameData.version = (Objects.requireNonNull(
                    installBinding.ET2.getEditText()).getText().toString().isEmpty() ?
                    null
                    : Objects.requireNonNull(
                    installBinding.ET2.getEditText()).getText().toString());
            passwordForArchive = (Objects.requireNonNull(
                    installBinding.ET3.getEditText()).getText().toString().isEmpty() ?
                    null
                    : Objects.requireNonNull(
                    installBinding.ET3.getEditText()).getText().toString());
            if (tempInstallFile != null) {
                gameData.fileSize = formatFileSize(tempInstallFile.length() , controller.binaryPrefixes);
            } else if (tempInstallDir != null) {
                gameData.fileSize = formatFileSize(dirSize(tempInstallDir) , controller.binaryPrefixes);
            }
            gameData.icon = (tempImageFile == null ? null : tempImageFile.getUri().toString());
            installGame(tempInstallFile != null ? tempInstallFile : tempInstallDir , gameData);
            isSelectArchive.set(false);
            isSelectFolder.set(false);
            dialogFragments.dismiss();
        } catch (NullPointerException ex) {
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog("Error: " + ex);
        }
    }

    public void showDialogEdit() {
        dialogFragments = new StockDialogFrags();
        dialogFragments.setDialogType(StockDialogType.EDIT_DIALOG);
        dialogFragments.setEditBinding(formingEditView());
        dialogFragments.onCancel(new DialogInterface() {
            @Override
            public void cancel() {
                isShowDialog.set(false);
            }
            @Override
            public void dismiss() {
            }
        });
        Objects.requireNonNull(activityObservableField.get())
                .showEditDialogFragment(dialogFragments);
        isShowDialog.set(true);
    }

    public void playGame() {
        var intent = new Intent(activityObservableField.get() ,
                GameActivity.class);
        intent.putExtra("gameId" , tempGameData.id);
        intent.putExtra("gameTitle" , tempGameData.title);
        intent.putExtra("gameDirUri" , tempGameData.gameDir.getAbsolutePath());
        var gameFileCount = tempGameData.gameFiles.size();
        switch (gameFileCount) {
            case 0:
                Objects.requireNonNull(activityObservableField.get())
                        .showErrorDialog("Game folder has no game files!");
                break;
            case 1:
                intent.putExtra("gameFileUri" , tempGameData.gameFiles.get(0).getAbsolutePath());
                Objects.requireNonNull(activityObservableField.get()).startGameActivity(intent);
                break;
            default:
                if (outputIntObserver.hasObservers()) {
                    outputIntObserver = new MutableLiveData<>();
                }
                var names = new ArrayList<String>();
                for (var file : tempGameData.gameFiles) {
                    names.add(file.getName());
                }
                var dialogFragments = new StockDialogFrags();
                dialogFragments.setDialogType(StockDialogType.SELECT_DIALOG);
                dialogFragments.setNames(names);
                Objects.requireNonNull(activityObservableField.get())
                        .showSelectDialogFragment(dialogFragments);
                outputIntObserver.observeForever(integer -> {
                    intent.putExtra("gameFileUri" ,
                            tempGameData.gameFiles.get(integer).getAbsolutePath());
                    Objects.requireNonNull(activityObservableField.get()).startGameActivity(intent);
                });
                break;
        }
    }

    public void createEditIntent() {
        try {
            var editTextTitle = editBinding.ET0.getEditText();
            if (editTextTitle != null) {
                if (editTextTitle.getText().toString().isEmpty()) {
                    tempGameData.title = removeExtension(tempGameData.title);
                } else {
                    tempGameData.title = editTextTitle.getText().toString();
                }
            }
            var editTextAuthor = editBinding.ET1.getEditText();
            if (editTextAuthor != null) {
                if (editTextAuthor.getText().toString().isEmpty()) {
                    tempGameData.author = removeExtension(tempGameData.author);
                } else {
                    tempGameData.author = editTextAuthor.getText().toString();
                }
            }
            var editTextVersion = editBinding.ET2.getEditText();
            if (editTextVersion != null) {
                if (editTextVersion.toString().isEmpty()) {
                    tempGameData.version = removeExtension(tempGameData.version);
                } else {
                    tempGameData.version = editTextVersion.getText().toString();
                }
            }
            if (tempImageFile != null) {
                tempGameData.icon = tempImageFile.getUri().toString();
            }
            if (tempGameData.fileSize == null || tempGameData.fileSize.isEmpty()) {
                tempGameData.fileSize = formatFileSize(dirSize(DocumentFile
                                .fromFile(tempGameData.gameDir)) , controller.binaryPrefixes);
            }
            writeGameInfo(tempGameData , tempGameData.gameDir);
            if (tempPathFile != null) {
                copyFile(activityObservableField.get() , tempPathFile , tempGameData.gameDir);
            }
            if (tempModFile != null) {
                copyFile(activityObservableField.get() , tempModFile ,
                        findFileRecursively(tempGameData.gameDir, "mods"));
            }
            refreshGamesDirectory();
            isShowDialog.set(false);
            dialogFragments.dismiss();
        } catch (NullPointerException ex) {
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog("Error: " + ex);
        }
    }

    @NonNull
    @Contract(value = " -> new", pure = true)
    private String[] getSupportArchiveType() {
        return new String[]{
                "application/x-7z-compressed" ,
                "application/rar" ,
                "application/zip" ,
                "application/x-freearc" ,
                "application/x-arj" ,
                "application/x-b1" ,
                "application/x-cfs-compressed" ,
                "application/x-gtar" ,
                "application/x-xar" ,
                "application/x-zoo"};
    }

    public void sendIntent(@NonNull View view) {
        int id = view.getId();
        if (id == R.id.buttonSelectArchive) {
            if (controller.isUseNewFilePicker) {
                Objects.requireNonNull(activityObservableField.get())
                        .showFilePickerDialog(getSupportArchiveType());
            } else {
                Objects.requireNonNull(activityObservableField.get())
                        .showFilePickerActivity(getSupportArchiveType());
            }
        } else if (id == R.id.buttonSelectFolder) {
            Objects.requireNonNull(activityObservableField.get())
                    .showDirPickerDialog();
        } else if (id == R.id.buttonSelectIcon) {
            Objects.requireNonNull(activityObservableField.get())
                    .showFilePickerActivity(new String[]{"image/png" , "image/jpeg"});
        } else if (id == R.id.buttonSelectPath) {
            Objects.requireNonNull(activityObservableField.get())
                    .showFilePickerActivity(new String[]{"application/octet-stream"});
        } else if (id == R.id.buttonSelectMod) {
            Objects.requireNonNull(activityObservableField.get())
                    .showFilePickerActivity(new String[]{"application/octet-stream"});
        }
    }

    @NonNull
    private DialogInstallBinding formingInstallView() {
        installBinding =
                DialogInstallBinding.inflate(LayoutInflater.from(activityObservableField.get()));
        installBinding.setStockVM(this);
        installBinding.buttonSelectArchive.setOnClickListener(v ->
                sendIntent(installBinding.buttonSelectArchive));
        installBinding.buttonSelectFolder.setOnClickListener(v ->
                sendIntent(installBinding.buttonSelectFolder));
        installBinding.buttonSelectIcon.setOnClickListener(v ->
                sendIntent(installBinding.buttonSelectIcon));
        installBinding.installBT.setOnClickListener(v ->
                createInstallIntent());
        return installBinding;
    }

    @NonNull
    private DialogEditBinding formingEditView() {
        editBinding =
                DialogEditBinding.inflate(LayoutInflater.from(activityObservableField.get()));
        editBinding.setStockVM(this);

        if (!tempGameData.icon.isEmpty()) {
            Picasso.get()
                    .load(tempGameData.icon)
                    .fit()
                    .into(editBinding.imageView);
        }

        editBinding.buttonSelectPath.setOnClickListener(this::sendIntent);
        editBinding.buttonSelectMod.setOnClickListener(this::sendIntent);
        editBinding.buttonSelectIcon.setOnClickListener(this::sendIntent);
        editBinding.editBT.setOnClickListener(v -> createEditIntent());
        return editBinding;
    }
    // endregion Dialog

    // region Game install
    @NonNull
    public File getOrCreateGameDirectory(String gameName) {
        var folderName = normalizeFolderName(gameName);
        return getOrCreateDirectory(gamesDir , folderName);
    }

    public void installGame(DocumentFile gameFile , GameData gameData) {
        if (!isWritableDirectory(gamesDir)) {
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog("Games directory is not writable");
            return;
        }
        doInstallGame(gameFile , gameData);
    }

    private void doInstallGame(DocumentFile gameFile , GameData gameData) {
        var gameDir = getOrCreateGameDirectory(gameData.title);
        if (!isWritableDirectory(gameDir)) {
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog("Games directory is not writable");
            return;
        }
        if (!gameFile.isDirectory()) {
            notificationStateGame(gameData.title);
        }

        var installer = new Installer(activityObservableField.get());
        if (passwordForArchive != null) {
            installer.setPasswordForArchive(passwordForArchive);
        }
        installer.getErrorCode().observeForever(error -> {
            if (error != null) {
                if (error.equals("PasswordNotFound")) {
                    Objects.requireNonNull(activityObservableField.get()).showErrorDialog("Empty password");
                }
                if (error.equals("NIG")
                        && getStockActivity() != null) {
                    String message = getStockActivity()
                            .getString(R.string.installError)
                            .replace("-GAMENAME-" , gameData.title);
                    Objects.requireNonNull(activityObservableField.get()).showErrorDialog(message);
                }
                if (error.equals("NFE")
                        && getStockActivity() != null) {
                    String message = getStockActivity()
                            .getString(R.string.noGameFilesError);
                    Objects.requireNonNull(activityObservableField.get()).showErrorDialog(message);
                }
            }
        });
        installer.gameInstall(gameFile , gameDir).observeForever(aBoolean -> {
            if (aBoolean) {
                writeGameInfo(gameData , gameDir);
                refreshGames();
            }
        });

        isShowDialog.set(false);
    }

    private void notificationStateGame(String gameName) {
        var builder =
                new NotifyBuilder(activityObservableField.get() , "gameInstallationProgress");
        builder.createStatusChannel();
        builder.createProgressChannel();
        if (getStockActivity() != null) {
            builder.setTitleNotify(getStockActivity().getString(R.string.titleNotify));
            builder.setTextNotify(getStockActivity().getString(R.string.textProgressNotify));
            var notificationManager =
                    NotificationManagerCompat.from(getApplication());
            if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1 , builder.buildWithProgress());
                ArchiveUtil.progressInstall.observeForever(aLong -> {
                    if ((aLong % 2) == 0) {
                        notificationManager.notify(1 , builder.updateProgress((int) (aLong * 100 / ArchiveUtil.totalSize)));
                    }
                    if (aLong == ArchiveUtil.totalSize) {
                        notificationManager.cancelAll();
                        var notifyBuilder =
                                new NotifyBuilder(activityObservableField.get() , "gameInstalled");
                        notifyBuilder.setTitleNotify(getStockActivity().getString(R.string.titleNotify));
                        var tempMessage = getStockActivity()
                                .getString(R.string.textInstallNotify)
                                .replace("-GAMENAME-" , gameName);
                        notifyBuilder.setTextNotify(tempMessage);
                        notificationManager.notify(1 , notifyBuilder.buildWithoutProgress());
                    }
                });
            }
        } else {
            builder.setTitleNotify(getApplication().getString(R.string.titleNotify));
            builder.setTextNotify(getApplication().getString(R.string.textProgressNotify));
            var notificationManager =
                    NotificationManagerCompat.from(getApplication());
            if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1 , builder.buildWithProgress());
                ArchiveUtil.progressInstall.observeForever(aLong -> {
                    if ((aLong % 2) == 0) {
                        notificationManager.notify(1 , builder.updateProgress((int) (aLong * 100 / ArchiveUtil.totalSize)));
                    }
                    if (aLong == ArchiveUtil.totalSize) {
                        notificationManager.cancelAll();
                        var notifyBuilder =
                                new NotifyBuilder(activityObservableField.get() , "gameInstalled");
                        notifyBuilder.setTitleNotify(getApplication().getString(R.string.titleNotify));
                        var tempMessage = getApplication()
                                .getString(R.string.textInstallNotify)
                                .replace("-GAMENAME-" , gameName);
                        notifyBuilder.setTextNotify(tempMessage);
                        notificationManager.notify(1 , notifyBuilder.buildWithoutProgress());
                    }
                });
            }
        }
    }

    public void writeGameInfo(GameData gameData , File gameDir) {
        var infoFile = findFileOrDirectory(gameDir, GAME_INFO_FILENAME);
        if (infoFile == null) {
            infoFile = createFile(gameDir, GAME_INFO_FILENAME);
        }
        if (!isWritableFile(infoFile)) {
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog("Game data info file is not writable");
            return;
        }
        try (var out = new FileOutputStream(infoFile);
             var writer = new OutputStreamWriter(out)) {
            writer.write(objectToXml(gameData));
        } catch (Exception ex) {
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog("Failed to write to a gameData info file");
        }
    }
    // endregion Game install

    // region Refresh
    public void refreshGamesDirectory() {
        var extFilesDir = getApplication().getExternalFilesDir(null);
        if (extFilesDir == null) {
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog("External files directory not found");
            return;
        }
        var dir = getOrCreateDirectory(extFilesDir, "games");
        if (!isWritableDirectory(dir)
                && getStockActivity() != null) {
            var message = "Games directory is not writable" + " " +
                    getStockActivity().getString(R.string.gamesDirError);
            Objects.requireNonNull(activityObservableField.get())
                    .showErrorDialog(message);
            return;
        }
        setGamesDir(dir);
        refreshGames();
    }

    public void refreshGames() {
        gamesMap.clear();
        for (var localGameData : localGame.getGames(gamesDir)) {
            var remoteGameData = gamesMap.get(localGameData.id);
            if (remoteGameData != null) {
                var aggregateGameData = new GameData(remoteGameData);
                aggregateGameData.gameDir = localGameData.gameDir;
                aggregateGameData.gameFiles = localGameData.gameFiles;
                gamesMap.put(localGameData.id, aggregateGameData);
            } else {
                gamesMap.put(localGameData.id, localGameData);
            }
        }
        setLocalGameDataList();
    }

    public void refreshGames(ArrayList<GameData> gameDataArrayList) {
        gamesMap.clear();
        for (var localGameData : gameDataArrayList) {
            var remoteGameData = gamesMap.get(localGameData.id);
            if (remoteGameData != null) {
                var aggregateGameData = new GameData(remoteGameData);
                aggregateGameData.gameDir = localGameData.gameDir;
                aggregateGameData.gameFiles = localGameData.gameFiles;
                gamesMap.put(localGameData.id, aggregateGameData);
            } else {
                gamesMap.put(localGameData.id, localGameData);
            }
        }

        HashMap<String, GameData> tempGamesMap = new HashMap<>();
        for (var localGameData : localGame.getGames(gamesDir)) {
            var remoteGameData = tempGamesMap.get(localGameData.id);
            if (remoteGameData != null) {
                var aggregateGameData = new GameData(remoteGameData);
                aggregateGameData.gameDir = localGameData.gameDir;
                aggregateGameData.gameFiles = localGameData.gameFiles;
                tempGamesMap.put(localGameData.id, aggregateGameData);
            } else {
                tempGamesMap.put(localGameData.id, localGameData);
            }
        }
        gamesMap.putAll(tempGamesMap);
        setLocalGameDataList();
    }
    // endregion Refresh

    // region Plugin
    public boolean isDownloadPlugin () {
        var pluginClient = new PluginClient();
        pluginClient.loadListPlugin(getApplication());
        return pluginClient.getNamePlugin() != null
                && pluginClient.getNamePlugin().equals("org.qp.android.plugin.AidlService");
    }

    public void startDownloadPlugin () {
        var pluginClient = new PluginClient();
        pluginClient.loadListPlugin(getApplication());
        pluginClient.connectPlugin(getApplication() , PluginType.DOWNLOAD_PLUGIN);
        new Handler().postDelayed(() -> {
            try {
                pluginClient.getQuestPlugin().arrayGameData(new AsyncCallback.Stub() {
                    @Override
                    public void onSuccess(List<GameDataParcel> gameDataParcel) throws RemoteException {
                        refreshGames(convertDTO(gameDataParcel));
                        setGameDataList(convertDTO(gameDataParcel));
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG , "Error: " , e);
            }
        } , 1000);
    }

    @NonNull
    private ArrayList<GameData> convertDTO (List<GameDataParcel> gameDataParcelList) {
        var gameDataArrayList = new ArrayList<GameData>();
        for (GameDataParcel gameDataParcel : gameDataParcelList) {
            var gameData = new GameData();
            gameData.id = gameDataParcel.id;
            gameData.listId = gameDataParcel.listId;
            gameData.author = gameDataParcel.author;
            gameData.portedBy = gameDataParcel.portedBy;
            gameData.version = gameDataParcel.version;
            gameData.title = gameDataParcel.title;
            gameData.lang = gameDataParcel.lang;
            gameData.player = gameDataParcel.player;
            gameData.icon = gameDataParcel.icon;
            gameData.fileUrl = gameDataParcel.fileUrl;
            gameData.fileSize = gameDataParcel.fileSize;
            gameData.fileExt = gameDataParcel.fileExt;
            gameData.descUrl = gameDataParcel.descUrl;
            gameData.pubDate = gameDataParcel.pubDate;
            gameData.modDate = gameDataParcel.modDate;
            gameData.gameDir = gameDataParcel.gameDir;
            gameData.gameFiles = gameDataParcel.gameFiles;
            gameDataArrayList.add(gameData);
        }
        return gameDataArrayList;
    }
    // endregion Plugin
}

