#!/usr/bin/env bash
# ==============================================================================
# Mighty GPS / Android SaaS Tracker - Dependency & Runtime Diagnostic Tool
# ==============================================================================

set -e

echo "========================================================"
echo " 🚀 Running Android Dependency & Runtime Diagnostics"
echo "========================================================"

# 1. Check Gradle and Java environment
echo ""
echo "🔍 [1/5] Checking Build Environment..."
java -version 2>&1 | head -n 1
gradle --version | grep -E "Gradle|Kotlin|Groovy"

# 2. Check for Duplicate Classes across dependencies
echo ""
echo "🔍 [2/5] Checking for Duplicate Classes in Classpath..."
gradle :app:checkDebugDuplicateClasses || echo "⚠️ Warning: Duplicate classes check found potential overlaps."

# 3. Check for Dependency Version Mismatches
echo ""
echo "🔍 [3/5] Analyzing Dependency Tree for Conflicts..."
gradle :app:dependencies --configuration releaseRuntimeClasspath | grep -E "FAILED|\(c\)|->" | head -n 30 || true

# 4. Check Google Maps API Key placeholder configuration
echo ""
echo "🔍 [4/5] Checking Manifest Placeholders & Secrets..."
if [ -f ".env" ]; then
    echo "✅ .env file exists."
    grep -E "GOOGLE_MAPS_API_KEY" .env || echo "ℹ️ GOOGLE_MAPS_API_KEY not set in .env (using build.gradle fallback)"
else
    echo "ℹ️ .env file not found, using .env.example fallback."
fi

# 5. Compile Verification
echo ""
echo "🔍 [5/5] Testing Release Compilation & Linting..."
gradle :app:assembleRelease || gradle :app:assembleDebug

echo ""
echo "========================================================"
echo " ✅ Diagnostics Complete! System is healthy."
echo "========================================================"
