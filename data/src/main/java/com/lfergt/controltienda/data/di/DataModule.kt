package com.lfergt.controltienda.data.di

import com.lfergt.controltienda.data.openfoodfacts.OpenFoodFactsApi
import com.lfergt.controltienda.data.openfoodfacts.OpenFoodFactsLookup
import com.lfergt.controltienda.data.report.ReportExporterImpl
import com.lfergt.controltienda.data.repository.AdminRepositoryImpl
import com.lfergt.controltienda.data.repository.AuthRepositoryImpl
import com.lfergt.controltienda.data.repository.CatalogRepositoryImpl
import com.lfergt.controltienda.data.repository.MemberRepositoryImpl
import com.lfergt.controltienda.data.repository.NotificationRepositoryImpl
import com.lfergt.controltienda.data.repository.OrderRepositoryImpl
import com.lfergt.controltienda.data.repository.ReceptionRepositoryImpl
import com.lfergt.controltienda.data.repository.StoreRepositoryImpl
import com.lfergt.controltienda.data.repository.SupplierRepositoryImpl
import com.lfergt.controltienda.data.repository.UserRepositoryImpl
import com.lfergt.controltienda.data.system.ConnectivityMonitorImpl
import com.lfergt.controltienda.data.system.SyncMonitorImpl
import com.lfergt.controltienda.data.update.GitHubApi
import com.lfergt.controltienda.data.update.GitHubHttp
import com.lfergt.controltienda.data.update.GitHubUpdates
import com.lfergt.controltienda.domain.port.AdminRepository
import com.lfergt.controltienda.domain.port.AppUpdates
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.CatalogRepository
import com.lfergt.controltienda.domain.port.Clock
import com.lfergt.controltienda.domain.port.ConnectivityMonitor
import com.lfergt.controltienda.domain.port.MemberRepository
import com.lfergt.controltienda.domain.port.NotificationRepository
import com.lfergt.controltienda.domain.port.OrderRepository
import com.lfergt.controltienda.domain.port.ProductInfoLookup
import com.lfergt.controltienda.domain.port.ReceptionRepository
import com.lfergt.controltienda.domain.port.ReportExporter
import com.lfergt.controltienda.domain.port.StoreRepository
import com.lfergt.controltienda.domain.port.SupplierRepository
import com.lfergt.controltienda.domain.port.SyncMonitor
import com.lfergt.controltienda.domain.port.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Une cada puerto del dominio con su adaptador. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun users(impl: UserRepositoryImpl): UserRepository
    @Binds abstract fun stores(impl: StoreRepositoryImpl): StoreRepository
    @Binds abstract fun members(impl: MemberRepositoryImpl): MemberRepository
    @Binds abstract fun notifications(impl: NotificationRepositoryImpl): NotificationRepository
    @Binds abstract fun catalog(impl: CatalogRepositoryImpl): CatalogRepository
    @Binds abstract fun orders(impl: OrderRepositoryImpl): OrderRepository
    @Binds abstract fun suppliers(impl: SupplierRepositoryImpl): SupplierRepository
    @Binds abstract fun receptions(impl: ReceptionRepositoryImpl): ReceptionRepository
    @Binds abstract fun admin(impl: AdminRepositoryImpl): AdminRepository
    @Binds abstract fun productInfo(impl: OpenFoodFactsLookup): ProductInfoLookup
    @Binds abstract fun exporter(impl: ReportExporterImpl): ReportExporter
    @Binds abstract fun connectivity(impl: ConnectivityMonitorImpl): ConnectivityMonitor
    @Binds abstract fun sync(impl: SyncMonitorImpl): SyncMonitor
    @Binds abstract fun updates(impl: GitHubUpdates): AppUpdates
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun clock(): Clock = Clock.SYSTEM

    @Provides
    @Singleton
    fun openFoodFacts(config: DataConfig): OpenFoodFactsApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            // Open Food Facts pide identificar la app en el User-Agent.
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "ControlTienda/${config.appVersion} (Android; github.com/L-FER-GT)")
                        .build(),
                )
            }
            .build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsApi::class.java)
    }

    @Provides
    @Singleton
    @GitHubHttp
    fun gitHubHttp(config: DataConfig): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // GitHub rechaza las peticiones sin User-Agent.
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "ControlTienda/${config.appVersion}").build())
        }
        .build()

    @Provides
    @Singleton
    fun gitHubApi(@GitHubHttp client: OkHttpClient): GitHubApi {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }
}
