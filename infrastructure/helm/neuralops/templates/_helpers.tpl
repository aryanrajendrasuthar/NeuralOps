{{/*
Expand the name of the chart.
*/}}
{{- define "neuralops.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "neuralops.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "neuralops.labels" -}}
app.kubernetes.io/name: {{ include "neuralops.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: neuralops
{{- end }}

{{/*
Selector labels — used for Deployment selectors and Service selectors.
*/}}
{{- define "neuralops.selectorLabels" -}}
app.kubernetes.io/name: {{ include "neuralops.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Image name for a given service, respecting global registry override.
Usage: {{ include "neuralops.image" (dict "registry" .Values.global.imageRegistry "name" "trace-ingestion-service" "tag" .Values.image.tag) }}
*/}}
{{- define "neuralops.image" -}}
{{- if .registry -}}
{{ .registry }}/neuralops/{{ .name }}:{{ .tag }}
{{- else -}}
neuralops/{{ .name }}:{{ .tag }}
{{- end }}
{{- end }}
