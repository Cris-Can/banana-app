#!/bin/bash
# check-legacy-fields.sh
# Script para asegurar que no se reintroduzcan campos legacy 'isAdmin'

echo "🔍 Buscando referencias legacy a 'isAdmin'..."

# Buscar en archivos Kotlin, TS y Rules
RESULTS=$(grep -r "isAdmin" --include="*.kt" --include="*.ts" --include="*.rules" .)

if [ -z "$RESULTS" ]; then
    echo "✅ No se encontraron referencias legacy. El código está limpio."
    exit 0
else
    echo "❌ ERROR: Se encontraron referencias a 'isAdmin' en los siguientes archivos:"
    echo "$RESULTS"
    echo "Por favor, utiliza el campo canónico 'admin' en su lugar."
    exit 1
fi
