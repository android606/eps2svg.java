package com.convert2web.ps;

import com.convert2web.image.AgmEmbeddedImage;
import com.convert2web.image.AgmPlaceholderTiles;
import com.convert2web.image.AgmTileDecoder;
import com.convert2web.model.Matrix;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records AGM {@code %%BeginBinary} tiles during PostScript VM execution.
 */
final class AgmImagePaint {
    private static final Logger logger = Logger.getLogger(AgmImagePaint.class.getName());

    private AgmImagePaint() {
    }

    static void apply(PostScriptVm vm, PsValue.AgmBinaryInvokeValue invoke) {
        if (vm.operandCount() == 0) {
            return;
        }
        PsValue top = vm.peek();
        Map<String, PsValue> entries = dictionaryEntries(top);
        if (entries == null) {
            return;
        }
        Matrix ctm = vm.getGraphicsState().getCtm();
        AgmEmbeddedImage image = AgmTileDecoder.decodeBeginBinary(
                entries,
                invoke.getOperator(),
                invoke.getPayload(),
                ctm,
                vm.getIndexedPaletteContext());
        if (image == null) {
            logger.log(Level.WARNING, "AGM tile skipped ({0}): decode returned no image",
                    invoke.getOperator());
            vm.pop();
            return;
        }
        boolean paintsPendingText = AgmPlaceholderTiles.isBlankSepcsPlaceholder(
                image.getPngBytes(), image.getWidth(), image.getHeight(), image.getCtm());
        vm.getDocumentRecorder().recordEmbeddedImage(
                image.getWidth(),
                image.getHeight(),
                image.getCtm(),
                image.getPngBytes(),
                paintsPendingText);
        vm.pop();
    }

    private static Map<String, PsValue> dictionaryEntries(PsValue value) {
        if (value instanceof PsValue.DictionaryValue) {
            return ((PsValue.DictionaryValue) value).getEntries();
        }
        if (value instanceof PsValue.RuntimeDictionaryValue) {
            return ((PsValue.RuntimeDictionaryValue) value).getDictionary().snapshot();
        }
        return null;
    }
}
