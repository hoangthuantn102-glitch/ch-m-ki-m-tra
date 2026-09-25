package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "grading_results")
data class GradingResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentName: String,
    val className: String = "",
    val examName: String = "",
    val score: Double,
    val maxScore: Double,
    val totalQuestions: Int,
    val gradedCount: Int,
    val correctCount: Int,
    val testType: String, // CIRCLED_ON_SHEET or ANSWER_TABLE
    val answersJson: String, // JSON serialized List<StudentAnswerDetail>
    val timestamp: Long,
    val imagePath: String?
) {
    fun toDomain(moshi: Moshi): GradingResult {
        val type = Types.newParameterizedType(List::class.java, StudentAnswerDetail::class.java)
        val adapter = moshi.adapter<List<StudentAnswerDetail>>(type)
        val answersList = try {
            adapter.fromJson(answersJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return GradingResult(
            id = id,
            studentName = studentName,
            className = className,
            examName = examName,
            score = score,
            maxScore = maxScore,
            totalQuestions = totalQuestions,
            gradedCount = gradedCount,
            correctCount = correctCount,
            testType = TestType.valueOf(testType),
            answers = answersList,
            timestamp = timestamp,
            imagePath = imagePath
        )
    }

    companion object {
        fun fromDomain(domain: GradingResult, moshi: Moshi): GradingResultEntity {
            val type = Types.newParameterizedType(List::class.java, StudentAnswerDetail::class.java)
            val adapter = moshi.adapter<List<StudentAnswerDetail>>(type)
            val json = adapter.toJson(domain.answers)

            return GradingResultEntity(
                id = domain.id,
                studentName = domain.studentName,
                className = domain.className,
                examName = domain.examName,
                score = domain.score,
                maxScore = domain.maxScore,
                totalQuestions = domain.totalQuestions,
                gradedCount = domain.gradedCount,
                correctCount = domain.correctCount,
                testType = domain.testType.name,
                answersJson = json,
                timestamp = domain.timestamp,
                imagePath = domain.imagePath
            )
        }
    }
}

@Dao
interface GradingResultDao {
    @Query("SELECT * FROM grading_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<GradingResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: GradingResultEntity): Long

    @Query("UPDATE grading_results SET studentName = :studentName WHERE id = :id")
    suspend fun updateStudentName(id: Int, studentName: String)

    @Query("DELETE FROM grading_results WHERE id = :id")
    suspend fun deleteResult(id: Int)

    @Query("DELETE FROM grading_results")
    suspend fun deleteAllResults()
}

@Entity(tableName = "exam_configs")
data class ExamConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val examName: String,
    val className: String,
    val testType: String,
    val totalQuestions: Int,
    val pointsPerCorrect: Double,
    val masterKeysJson: String,
    val selectedQuestionsJson: String,
    val totalPages: Int = 1
) {
    fun toDomain(moshi: Moshi): ExamConfig {
        val keysType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        val keysAdapter = moshi.adapter<Map<String, String>>(keysType)
        val masterKeysMap = try {
            val rawMap = keysAdapter.fromJson(masterKeysJson) ?: emptyMap()
            rawMap.mapKeys { it.key.toIntOrNull() ?: 1 }
        } catch (e: Exception) {
            emptyMap()
        }

        val questionsType = Types.newParameterizedType(Set::class.java, Int::class.javaObjectType)
        val questionsAdapter = moshi.adapter<Set<Int>>(questionsType)
        val selectedQuestionsSet = try {
            questionsAdapter.fromJson(selectedQuestionsJson) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }

        return ExamConfig(
            id = id,
            examName = examName,
            className = className,
            testType = TestType.valueOf(testType),
            totalQuestions = totalQuestions,
            pointsPerCorrect = pointsPerCorrect,
            masterKeys = masterKeysMap,
            selectedQuestions = selectedQuestionsSet,
            totalPages = totalPages
        )
    }

    companion object {
        fun fromDomain(domain: ExamConfig, moshi: Moshi): ExamConfigEntity {
            val keysType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
            val keysAdapter = moshi.adapter<Map<String, String>>(keysType)
            val stringKeysMap = domain.masterKeys.mapKeys { it.key.toString() }
            val keysJson = keysAdapter.toJson(stringKeysMap)

            val questionsType = Types.newParameterizedType(Set::class.java, Int::class.javaObjectType)
            val questionsAdapter = moshi.adapter<Set<Int>>(questionsType)
            val questionsJson = questionsAdapter.toJson(domain.selectedQuestions)

            return ExamConfigEntity(
                id = domain.id,
                examName = domain.examName,
                className = domain.className,
                testType = domain.testType.name,
                totalQuestions = domain.totalQuestions,
                pointsPerCorrect = domain.pointsPerCorrect,
                masterKeysJson = keysJson,
                selectedQuestionsJson = questionsJson,
                totalPages = domain.totalPages
            )
        }
    }
}

@Dao
interface ExamConfigDao {
    @Query("SELECT * FROM exam_configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<ExamConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ExamConfigEntity): Long

    @Query("DELETE FROM exam_configs WHERE id = :id")
    suspend fun deleteConfig(id: Int)

    @Query("DELETE FROM exam_configs")
    suspend fun deleteAllConfigs()
}

@Database(entities = [GradingResultEntity::class, ExamConfigEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gradingResultDao(): GradingResultDao
    abstract fun examConfigDao(): ExamConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2 migration
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE grading_results ADD COLUMN className TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE grading_results ADD COLUMN examName TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE exam_configs ADD COLUMN totalPages INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "exam_grader_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GradingRepository(
    private val resultDao: GradingResultDao,
    private val configDao: ExamConfigDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val allResults: Flow<List<GradingResult>> = resultDao.getAllResults().map { entities ->
        entities.map { it.toDomain(moshi) }
    }

    val allConfigs: Flow<List<ExamConfig>> = configDao.getAllConfigs().map { entities ->
        entities.map { it.toDomain(moshi) }
    }

    suspend fun insert(result: GradingResult): Long {
        val entity = GradingResultEntity.fromDomain(result, moshi)
        return resultDao.insertResult(entity)
    }

    suspend fun updateStudentName(id: Int, studentName: String) {
        resultDao.updateStudentName(id, studentName)
    }

    suspend fun delete(id: Int) {
        resultDao.deleteResult(id)
    }

    suspend fun clearAll() {
        resultDao.deleteAllResults()
    }

    suspend fun insertConfig(config: ExamConfig): Long {
        val entity = ExamConfigEntity.fromDomain(config, moshi)
        return configDao.insertConfig(entity)
    }

    suspend fun deleteConfig(id: Int) {
        configDao.deleteConfig(id)
    }

    suspend fun clearAllConfigs() {
        configDao.deleteAllConfigs()
    }
}
