const langData = window.LANG_DE || {};
const widgetLang = window.WIDGETS_DE || {};

applyLang(langData);
applyAnswerSkeletons(langData);
window.S4_SCENARIOS = typeof buildS4Scenarios === "function"
  ? buildS4Scenarios(widgetLang.s4Scenarios)
  : [];
setupNavigation();
setupSectionViews();
setupIntroCalculator(langData);
setupEthicsForm(langData);
setupHiddenFollowups();
setupTextareaTabIndent();
setupGlossTooltips();
setupMaterialFolderDownload();
setupHelpToggles(langData);
setupBitflipWidget(widgetLang.bitflip);
setupRLEWidget(widgetLang.rle);
setupRLETextWidget(widgetLang.rleText);
setupDictWidget(widgetLang.dict);
setupBlockAvgWidget(widgetLang.blockAvg);
setupBlockSizeWidget(widgetLang.blockSize);
setupLossyClosingWidget(widgetLang.lossyClosing);
setupTextVsJpegWidget(widgetLang.textVsJpeg);
setupFileInspectorWidget(widgetLang.fileInspector);
setupZipArchiveWidget(widgetLang.zipArchive);
setupFilesystemWidget(widgetLang.filesystem);
setupFilesystemSimulator(widgetLang.filesystemSimulator);
setupEfficiencyWidget(widgetLang.efficiency);
setupWidgetFullscreen(langData);
setupJPEGIntroSlideshow(langData);
setupReflectionCompare();
