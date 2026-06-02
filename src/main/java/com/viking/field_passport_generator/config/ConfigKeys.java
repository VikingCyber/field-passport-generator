package com.viking.field_passport_generator.config;

public final class ConfigKeys {

    // ===== APP =====
    public static final class App {
        public static final String MODE = "app.mode";

        public static final class Server {
            public static final String PORT = "app.server.port";
            public static final String HOST = "app.server.host";
        }

        public static final class Locale {
            public static final String TIMEZONE = "app.locale.timezone";
            public static final String DEFAULT_EMPTY_LABEL = "app.locale.default-empty-label";
            public static final String EQUIPMENT_SEPARATOR = "app.locale.equipment-separator";
        }

        public static final class Performance {
            public static final String MAX_CONCURRENT_TASKS = "app.performance.max-concurrent-tasks";
            public static final String AGGREGATION_THRESHOLD_HOURS = "app.performance.aggregation-threshold-time";
        }

        public static final class Storage {
            public static final String OUTPUT_DIR = "app.storage.output-dir";
            public static final String MIN_FREE_SPACE_MB = "app.storage.min-free-space-mb";
            public static final String PASSPORT_EXTENSION = "app.storage.passport-extension";
            public static final String CACHE_BASE_DIR = "app.storage.cache-base-dir";

            public static final class Notes {
                public static final String DIR = "app.storage.notes.dir";
                public static final String EXTENSION = "app.storage.notes.extension";
            }

            public static final class Satellite {
                public static final String EXTENSION = "app.storage.satellite.extension";
            }

            public static final class Charts {
                public static final String DIR = "app.storage.charts.dir";
                public static final String EXTENSION = "app.storage.charts.extension";
                public static final String PREFIX = "app.storage.charts.prefix";
                public static final String WIDTH = "app.storage.charts.width";
                public static final String HEIGHT = "app.storage.charts.height";
                public static final String FONT_PATH = "app.storage.charts.font-path";
            }
        }

        public static final class LocalFiles {
            public static final String FIELD_DATA = "app.local-files.field-data-path";
            public static final String OPERATIONS = "app.local-files.operations-path";
            public static final String TMC = "app.local-files.tmc-path";
            public static final String NOTES = "app.local-files.notes-path";
            public static final String UNITS = "app.local-files.units-path";
        }
    }

    // ===== AGRO =====
    public static final class Agro {

        public static final class Api {
            public static final String KEY = "agro.api.key";
            public static final String BASE_URL = "agro.api.base-url";
            public static final String USER_AGENT = "agro.api.user-agent";
            public static final String MIN_DOWNLOAD_SIZE_BYTES = "agro.api.min-download-size-bytes";
            public static final String RECOVERY_TIME_MS = "agro.api.recovery-time-ms";

            public static final class Endpoints {
                public static final String ATTACHMENTS_INFO = "agro.api.endpoints.attachments-info";
                public static final String SPECTRAL_INDICES = "agro.api.endpoints.spectral-indices";
                public static final String FIELD_REPORT = "agro.api.endpoints.field-report";
                public static final String TMC = "agro.api.endpoint.tmc";
                public static final String UNITS = "agro.api.endpoint.units";
                public static final String NOTES = "agro.api.endpoint.notes";
            }
        }

        public static final class Sync {
            public static final String FROM_DATE = "agro.sync.from-date";
            public static final String TO_DATE = "agro.sync.to-date";
        }

        public static final class Notes {
            public static final String MAX_CONCURRENT_REQUESTS = "agro.notes.max-concurrent-requests";
        }

        public static final class Satellite {
            public static final String MAX_CONCURRENT_REQUESTS = "agro.satellite.max-concurrent-requests";
            public static final String CLOUD_THRESHOLD = "agro.satellite.cloud-threshold";
            public static final String CLOUD_WEIGHT_FACTOR = "agro.satellite.cloud-weight-factor";
            public static final String SCAN_WINDOW_DAYS = "agro.satellite.scan-window-days";
            public static final String FROM_DATE = "agro.satellite.from-date";
            public static final String TO_DATE = "agro.satellite.to-date";
            public static final String INDICES = "agro.satellite.indices";
            public static final String MAPPING = "agro.satellite.mapping";
        }
    }

    private ConfigKeys() {}
}